package vn.ptit.colorgame;

import javax.sound.sampled.*;
import java.awt.GraphicsEnvironment;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Per-client audio. All decoding, mixer calls and clip disposal run on one daemon worker.
 * Commands never block the Swing EDT. Unavailable assets/devices simply remain silent. */
public final class AudioManager implements AutoCloseable {
    public enum Sound {
        CLICK("click",0,70), ORB("orb",1,55), SWAP("swap",1,90), SUBMIT("submit",2,160),
        INVITE("invite",3,350), ACCEPT("accept",2,180), REJECT("reject",2,180),
        TURN("turn",3,300), WARNING("warning",2,750), TIMEOUT("timeout",3,300),
        VICTORY("victory",4,500), DEFEAT("defeat",4,500), PEER_LEFT("peer-left",3,350),
        AMBIENT("ambient",-1,0), BATTLE_MUSIC("battle",-1,0);
        final String file; final int priority; final long gap;
        Sound(String file,int priority,long gap) { this.file=file; this.priority=priority; this.gap=gap*1_000_000; }
        public String resource() { return "/audio/"+file+".wav"; }
        public boolean isMusic() { return priority<0; }
    }
    /** The screen chooses a cached track; neither track participates in the effects queue. */
    public enum MusicScene {
        LOBBY(Sound.AMBIENT), BATTLE(Sound.BATTLE_MUSIC);
        final Sound track;
        MusicScene(Sound track) { this.track=track; }
    }
    interface Voice extends AutoCloseable {
        void start(boolean loop); void stop(); boolean running(); int position(); void close();
        default void volume(int percent) {}
    }
    interface Loader { Voice load(Sound sound) throws Exception; }
    public record Report(int loaded,int failed,int played,int dropped,int maxConcurrent,boolean musicRunning,int framesAdvanced) {}
    private final ThreadPoolExecutor worker;
    private final EnumMap<Sound,Voice> voices=new EnumMap<>(Sound.class);
    private final EnumMap<Sound,Long> last=new EnumMap<>(Sound.class);
    private final AtomicBoolean closed=new AtomicBoolean();
    private final CompletableFuture<Void> disposal=new CompletableFuture<>();
    private final Set<CompletableFuture<Report>> reports=ConcurrentHashMap.newKeySet();
    private volatile boolean effects=true, music, suspended;
    private volatile MusicScene musicScene=MusicScene.LOBBY;
    private volatile int effectsVolume=70, musicVolume=45;
    private int failed, played, dropped, maximum;

    public AudioManager() {
        this(GraphicsEnvironment.isHeadless() || Boolean.getBoolean("colorduel.audio.disabled") ? sound -> null : AudioManager::open);
    }
    AudioManager(Loader loader) {
        worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(32),r -> {
            Thread thread=new Thread(r,"colorduel-audio"); thread.setDaemon(true); return thread;
        },new ThreadPoolExecutor.AbortPolicy());
        command(() -> {
            for(Sound sound:Sound.values()) {
                if(closed.get()) break;
                try {
                    Voice voice=loader.load(sound);
                    if(voice==null) { failed++; continue; }
                    if(closed.get()) { safeClose(voice); break; } voice.volume(sound.isMusic() ? musicVolume : effectsVolume); voices.put(sound,voice);
                } catch(Exception | LinkageError unavailable) { failed++; }
            }
            syncMusic();
        },true);
    }
    public int effectsVolume() { return effectsVolume; }
    public int musicVolume() { return musicVolume; }
    public void setEffectsVolume(int percent) {
        effectsVolume=Math.max(0,Math.min(100,percent));
        command(() -> { for(var entry:voices.entrySet()) if(!entry.getKey().isMusic()) entry.getValue().volume(effectsVolume); if(effectsVolume==0) stopEffects(); },true);
    }
    public void setMusicVolume(int percent) {
        musicVolume=Math.max(0,Math.min(100,percent));
        command(() -> { for(var entry:voices.entrySet()) if(entry.getKey().isMusic()) entry.getValue().volume(musicVolume); syncMusic(); },true);
    }
    public MusicScene musicScene() { return musicScene; }
    public void setMusicScene(MusicScene scene) {
        if(scene==null || scene==musicScene) return;
        musicScene=scene; command(this::syncMusic,true);
    }
    public boolean effectsEnabled() { return effects; }
    public boolean musicEnabled() { return music; }
    public void setEffectsEnabled(boolean enabled) {
        effects=enabled; command(() -> { if(!effects) stopEffects(); },true);
    }
    public void setMusicEnabled(boolean enabled) { music=enabled; command(this::syncMusic,true); }
    /** Pause background music while the whole client is hidden, preserving the user's setting. */
    public void setSuspended(boolean value) { suspended=value; command(this::syncMusic,true); }
    public void play(Sound sound) {
        if(sound==null || sound.isMusic() || closed.get() || !effects || effectsVolume==0) return;
        long requested=System.nanoTime(); command(() -> playNow(sound,requested),false);
    }
    public void stopTransient() { command(() -> { stopEffects(); last.clear(); },true); }
    private void playNow(Sound sound,long requested) {
        if(closed.get() || !effects || effectsVolume==0) return;
        long now=System.nanoTime();
        if(now-requested>(sound.priority>=3 ? 1_500_000_000L : 350_000_000L) || now-last.getOrDefault(sound,0L)<sound.gap) { dropped++; return; }
        Voice voice=voices.get(sound); if(voice==null) return;
        List<Sound> running=new ArrayList<>();
        for(var entry:voices.entrySet()) if(!entry.getKey().isMusic() && entry.getValue().running()) running.add(entry.getKey());
        if(sound.priority==0 && running.stream().anyMatch(s -> s.priority>=1)) { dropped++; return; }
        if(sound.priority>=3) for(Sound active:running) if(active.priority<3) { voices.get(active).stop(); }
        running.removeIf(s -> !voices.get(s).running() || s==sound);
        if(running.size()>=2) {
            Sound lowest=running.stream().min(Comparator.comparingInt(s -> s.priority)).orElseThrow();
            if(lowest.priority>=sound.priority) { dropped++; return; } voices.get(lowest).stop();
        }
        try { voice.start(false); last.put(sound,now); played++; maximum=Math.max(maximum,(int)voices.entrySet().stream().filter(e -> !e.getKey().isMusic() && e.getValue().running()).count()); }
        catch(RuntimeException failure) { voices.remove(sound); safeClose(voice); failed++; }
    }
    private void stopEffects() { for(var entry:voices.entrySet()) if(!entry.getKey().isMusic()) try { entry.getValue().stop(); } catch(RuntimeException ignored) {} }
    private void syncMusic() {
        Sound target=musicScene.track;
        boolean enabled=!closed.get() && music && musicVolume>0 && !suspended;
        // Stop the previous loop before starting the new one, in either transition direction.
        for(Sound sound:Sound.values()) if(sound.isMusic() && (!enabled || sound!=target)) {
            Voice voice=voices.get(sound); if(voice!=null) try { voice.stop(); }
            catch(RuntimeException failure) { voices.remove(sound); safeClose(voice); failed++; }
        }
        Voice voice=voices.get(target);
        if(enabled && voice!=null) try { if(!voice.running()) voice.start(true); }
        catch(RuntimeException failure) { voices.remove(target); safeClose(voice); failed++; }
    }
    private void command(Runnable task,boolean control) {
        if(worker.isShutdown()) return;
        Runnable safe=() -> { try { task.run(); } catch(RuntimeException | LinkageError ignored) {} };
        try { worker.execute(safe); }
        catch(RejectedExecutionException full) {
            // Never run a rejected task on the caller/EDT. Control tasks displace one stale cue.
            if(control && !worker.isShutdown()) { worker.getQueue().poll(); try { worker.execute(safe); } catch(RejectedExecutionException ignored) {} }
        }
    }
    CompletableFuture<Void> whenClosed() { return disposal; }
    CompletableFuture<Report> report() {
        CompletableFuture<Report> result=new CompletableFuture<>();
        if(closed.get()) { result.completeExceptionally(new IllegalStateException("Audio closed")); return result; }
        reports.add(result); result.whenComplete((value,error) -> reports.remove(result));
        // Diagnostics are optional; saturation must not leave a waiting test/UI indefinitely.
        result.orTimeout(5,TimeUnit.SECONDS);
        command(() -> { int frames=voices.values().stream().mapToInt(Voice::position).sum(); Voice selected=voices.get(musicScene.track);
            result.complete(new Report(voices.size(),failed,played,dropped,maximum,selected!=null && selected.running(),frames)); },true);
        if(closed.get()) result.completeExceptionally(new IllegalStateException("Audio closed"));
        return result;
    }
    private static Voice open(Sound sound) throws Exception {
        InputStream resource=AudioManager.class.getResourceAsStream(sound.resource()); if(resource==null) return null;
        Clip clip=null;
        try(InputStream buffered=new BufferedInputStream(resource); AudioInputStream stream=AudioSystem.getAudioInputStream(buffered)) {
            clip=AudioSystem.getClip(); clip.open(stream);
            return new NativeVoice(clip);
        } catch(Exception | LinkageError failure) { if(clip!=null) clip.close(); throw failure; }
    }
    private static final class NativeVoice implements Voice {
        private final Clip clip;
        NativeVoice(Clip clip) { this.clip=clip; }
        public void volume(int percent) {
            if(clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl gain=(FloatControl)clip.getControl(FloatControl.Type.MASTER_GAIN);
                float decibels=percent<=0 ? gain.getMinimum() : (float)(20*Math.log10(percent/100.0));
                gain.setValue(Math.max(gain.getMinimum(),Math.min(gain.getMaximum(),decibels)));
            }
        }
        public void start(boolean loop) { clip.stop(); clip.setFramePosition(0); if(loop) clip.loop(Clip.LOOP_CONTINUOUSLY); else clip.start(); }
        public void stop() { clip.stop(); }
        public boolean running() { return clip.isRunning(); }
        public int position() { return clip.getFramePosition(); }
        public void close() { clip.stop(); clip.close(); }
    }
    private static void safeClose(Voice voice) { try { voice.close(); } catch(RuntimeException ignored) {} }
    @Override public void close() {
        if(!closed.compareAndSet(false,true)) return;
        worker.getQueue().clear();
        for(var report:reports) report.completeExceptionally(new IllegalStateException("Audio closed"));
        command(() -> { try { for(Voice voice:voices.values()) safeClose(voice); voices.clear(); } finally { disposal.complete(null); } },true); worker.shutdown();
    }
}
