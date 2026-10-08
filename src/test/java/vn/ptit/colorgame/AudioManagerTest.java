package vn.ptit.colorgame;

import javax.sound.sampled.*;
import javax.swing.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Audio assets and scheduling verified without requiring an installed mixer. */
public final class AudioManagerTest {
    private static int checks;
    static void check(boolean ok,String text) { if(!ok) throw new AssertionError(text); checks++; System.out.println("PASS AUDIO "+text); }
    static final class FakeVoice implements AudioManager.Voice {
        final AtomicInteger starts=new AtomicInteger(),closes=new AtomicInteger(),level=new AtomicInteger(); volatile boolean running;
        Runnable onStart=() -> {};
        private final boolean held;
        FakeVoice(boolean held) { this.held=held; }
        private void worker() { if(SwingUtilities.isEventDispatchThread()) throw new AssertionError("Mixer operation on EDT"); }
        public void start(boolean loop) { worker(); starts.incrementAndGet(); running=loop || held; onStart.run(); }
        public void volume(int percent) { worker(); level.set(percent); }
        public void stop() { worker(); running=false; }
        public boolean running() { worker(); return running; }
        public int position() { worker(); return starts.get()*100; }
        public void close() { worker(); running=false; closes.incrementAndGet(); }
    }
    static final class FakeLoader implements AudioManager.Loader {
        final EnumMap<AudioManager.Sound,FakeVoice> voices=new EnumMap<>(AudioManager.Sound.class);
        final AtomicInteger loads=new AtomicInteger(),maxMusicConcurrent=new AtomicInteger(); final boolean held;
        FakeLoader(boolean held) { this.held=held; }
        public AudioManager.Voice load(AudioManager.Sound sound) {
            if(SwingUtilities.isEventDispatchThread()) throw new AssertionError("Decoding on EDT");
            loads.incrementAndGet(); FakeVoice voice=new FakeVoice(held); voices.put(sound,voice);
            voice.onStart=() -> maxMusicConcurrent.accumulateAndGet((int)voices.entrySet().stream().filter(e -> e.getKey().isMusic() && e.getValue().running).count(),Math::max);
            return voice;
        }
        int starts(AudioManager.Sound sound) { return voices.get(sound).starts.get(); }
    }
    static AudioManager.Report settled(AudioManager audio) throws Exception { return audio.report().get(6,TimeUnit.SECONDS); }
    static void close(AudioManager audio) throws Exception { audio.close(); audio.whenClosed().get(6,TimeUnit.SECONDS); }
    private static void assets() throws Exception {
        for(AudioManager.Sound sound:AudioManager.Sound.values()) {
            try(InputStream input=AudioManagerTest.class.getResourceAsStream(sound.resource())) {
                check(input!=null,"Bundled asset "+sound);
                try(AudioInputStream stream=AudioSystem.getAudioInputStream(new BufferedInputStream(input))) {
                    AudioFormat f=stream.getFormat(); byte[] data=stream.readAllBytes();
                    check(f.getEncoding()==AudioFormat.Encoding.PCM_SIGNED && f.getSampleSizeInBits()==16 && f.getChannels()==1 && f.getSampleRate()==44100 && !f.isBigEndian(),"Portable PCM WAV "+sound);
                    double seconds=data.length/2.0/f.getSampleRate();
                    check(sound.isMusic() ? seconds==8 : seconds>=.05 && seconds<=1.3,"Short cue / loop duration "+sound);
                    int peak=0; for(int i=0;i<data.length;i+=2) peak=Math.max(peak,Math.abs((short)((data[i]&255)|(data[i+1]<<8))));
                    check(peak<=11200 && peak>100,"No clipping, soft source level "+sound);
                    if(sound.isMusic()) {
                        int first=(short)((data[0]&255)|(data[1]<<8)), last=(short)((data[data.length-2]&255)|(data[data.length-1]<<8));
                        check(Math.abs(first-last)<300,"Music loop seam has no large sample jump "+sound);
                    }
                }
            }
        }
    }
    private static void scheduling() throws Exception {
        FakeLoader loader=new FakeLoader(true); AudioManager audio=new AudioManager(loader);
        try {
            check(settled(audio).loaded()==15 && loader.loads.get()==15,"All clips preload once on worker");
            check(loader.voices.get(AudioManager.Sound.ORB).level.get()==70 && loader.voices.get(AudioManager.Sound.AMBIENT).level.get()==45,"Audible default levels applied on worker");
            check(audio.effectsEnabled() && !audio.musicEnabled(),"Quiet music default, effects enabled");
            audio.play(AudioManager.Sound.ORB); audio.play(AudioManager.Sound.SWAP); audio.play(AudioManager.Sound.SUBMIT);
            check(settled(audio).maxConcurrent()<=2,"Concurrent effects capped at two");
            int played=settled(audio).played(); audio.play(AudioManager.Sound.CLICK);
            check(settled(audio).played()==played,"Button cue suppressed while stronger effects play");
            audio.play(AudioManager.Sound.VICTORY); audio.play(AudioManager.Sound.VICTORY);
            check(settled(audio).maxConcurrent()<=2 && loader.starts(AudioManager.Sound.VICTORY)==1,"Important cue preempts weak cues and duplicate is suppressed");
            audio.setMusicEnabled(true); check(settled(audio).musicRunning(),"Ambient starts as a cached loop");
            audio.setEffectsEnabled(false); int muted=settled(audio).played(); audio.play(AudioManager.Sound.ORB);
            check(settled(audio).played()==muted && settled(audio).musicRunning(),"Effect mute leaves independent music enabled");
            audio.setSuspended(true); check(!settled(audio).musicRunning() && audio.musicEnabled(),"Hidden client pauses music while retaining setting");
            audio.setSuspended(false); check(settled(audio).musicRunning(),"Showing client resumes selected music");
            audio.setMusicEnabled(false); audio.setEffectsEnabled(true); audio.stopTransient(); audio.play(AudioManager.Sound.ORB);
            check(!settled(audio).musicRunning() && loader.starts(AudioManager.Sound.ORB)==2,"Effects resume independently and use cached clip");
            audio.setEffectsVolume(0); int zero=settled(audio).played(); audio.play(AudioManager.Sound.SUBMIT);
            check(settled(audio).played()==zero && !loader.voices.get(AudioManager.Sound.ORB).running,"Zero effect volume suppresses and stops cues");
            audio.setEffectsVolume(70); audio.setMusicVolume(80); audio.setMusicEnabled(true);
            check(settled(audio).musicRunning() && loader.voices.get(AudioManager.Sound.AMBIENT).level.get()==80,"Music gain updates cached clip without loading");
            audio.setMusicVolume(0); check(!settled(audio).musicRunning(),"Zero music volume stops loop even if enabled");
            audio.setMusicVolume(45); check(settled(audio).musicRunning(),"Positive music volume resumes enabled loop");
            audio.setMusicEnabled(false);
            for(int i=0;i<500;i++) audio.play(AudioManager.Sound.CLICK);
            check(settled(audio).maxConcurrent()<=2 && loader.loads.get()==15,"Cue spam remains bounded without reloading resources");
        } finally { close(audio); }
        audio.close(); check(loader.voices.values().stream().allMatch(v -> v.closes.get()==1),"Closing client disposes every cached clip exactly once");
        AtomicInteger attempts=new AtomicInteger(); AudioManager unavailable=new AudioManager(sound -> { attempts.incrementAndGet(); throw new LineUnavailableException("test mixer unavailable"); });
        try { unavailable.play(AudioManager.Sound.TURN); unavailable.setMusicEnabled(true); check(settled(unavailable).failed()==15 && attempts.get()==15,"Unavailable mixer/assets degrade to silence without failing"); }
        finally { close(unavailable); }
    }
    private static void musicScenes() throws Exception {
        FakeLoader loader=new FakeLoader(false); AudioManager audio=new AudioManager(loader);
        try {
            settled(audio); audio.setMusicEnabled(true);
            check(settled(audio).musicRunning() && audio.musicScene()==AudioManager.MusicScene.LOBBY,"Login/lobby selects calm cached track");
            audio.setMusicScene(AudioManager.MusicScene.BATTLE);
            check(settled(audio).musicRunning() && !loader.voices.get(AudioManager.Sound.AMBIENT).running && loader.starts(AudioManager.Sound.BATTLE_MUSIC)==1,"Battle replaces lobby loop instead of layering tracks");
            audio.stopTransient(); audio.setEffectsEnabled(false); audio.play(AudioManager.Sound.BATTLE_MUSIC);
            check(settled(audio).musicRunning() && loader.starts(AudioManager.Sound.BATTLE_MUSIC)==1,"Effect mute/cleanup cannot stop or trigger battle music");
            audio.setMusicScene(AudioManager.MusicScene.BATTLE); audio.setMusicVolume(80);
            check(settled(audio).musicRunning() && loader.starts(AudioManager.Sound.BATTLE_MUSIC)==1 && loader.voices.get(AudioManager.Sound.AMBIENT).level.get()==80 && loader.voices.get(AudioManager.Sound.BATTLE_MUSIC).level.get()==80,"Repeated scene and volume changes preserve playback and share gain");
            audio.setMusicEnabled(false); settled(audio); int lobbyStarts=loader.starts(AudioManager.Sound.AMBIENT);
            audio.setMusicScene(AudioManager.MusicScene.LOBBY);
            check(!settled(audio).musicRunning() && loader.starts(AudioManager.Sound.AMBIENT)==lobbyStarts,"Muted scene change stays silent");
            audio.setMusicEnabled(true); check(settled(audio).musicRunning() && loader.starts(AudioManager.Sound.AMBIENT)==lobbyStarts+1,"Unmuting resumes latest scene");
            audio.setSuspended(true); settled(audio); int battleStarts=loader.starts(AudioManager.Sound.BATTLE_MUSIC);
            audio.setMusicScene(AudioManager.MusicScene.BATTLE);
            check(!settled(audio).musicRunning() && loader.starts(AudioManager.Sound.BATTLE_MUSIC)==battleStarts,"Hidden client changes scene without starting audio");
            audio.setSuspended(false); check(settled(audio).musicRunning() && loader.starts(AudioManager.Sound.BATTLE_MUSIC)==battleStarts+1,"Visible client resumes the selected battle track");
            audio.setMusicVolume(0); audio.setMusicScene(AudioManager.MusicScene.LOBBY);
            check(!settled(audio).musicRunning(),"Zero volume stays silent across scene transitions");
            audio.setMusicVolume(45); check(settled(audio).musicRunning(),"Restoring volume starts latest selected track");
            for(int i=0;i<100;i++) audio.setMusicScene(i%2==0 ? AudioManager.MusicScene.BATTLE : AudioManager.MusicScene.LOBBY);
            check(settled(audio).musicRunning() && !loader.voices.get(AudioManager.Sound.BATTLE_MUSIC).running && loader.loads.get()==15 && loader.maxMusicConcurrent.get()==1,"Rapid transitions never overlap music or reload resources");
        } finally { close(audio); }
        AudioManager missing=new AudioManager(sound -> sound==AudioManager.Sound.BATTLE_MUSIC ? null : new FakeVoice(false));
        try {
            missing.setMusicEnabled(true); settled(missing); missing.setMusicScene(AudioManager.MusicScene.BATTLE);
            check(!settled(missing).musicRunning() && settled(missing).failed()==1,"Missing battle track fails silently and stops old lobby track");
            missing.setMusicScene(AudioManager.MusicScene.LOBBY); check(settled(missing).musicRunning(),"Available lobby music still resumes after missing battle asset");
        } finally { close(missing); }
    }
    private static void absentAssets() throws Exception {
        AudioManager audio=new AudioManager(sound -> {
            if(sound==AudioManager.Sound.ORB) return null;
            if(sound==AudioManager.Sound.CLICK) throw new UnsupportedAudioFileException("corrupt fixture");
            return new FakeVoice(false);
        });
        try {
            audio.play(AudioManager.Sound.ORB); audio.play(AudioManager.Sound.CLICK); audio.play(AudioManager.Sound.TURN);
            AudioManager.Report report=settled(audio);
            check(report.loaded()==13 && report.failed()==2 && report.played()==1,"Missing/corrupt resources stay silent while available cues still play");
        } finally { close(audio); }
    }
    private static void slowDevice() throws Exception {
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1); FakeVoice late=new FakeVoice(false);
        AudioManager audio=new AudioManager(sound -> { entered.countDown(); release.await(5,TimeUnit.SECONDS); return late; });
        try {
            check(entered.await(3,TimeUnit.SECONDS),"Simulated slow mixer is loading");
            AtomicLong elapsed=new AtomicLong(); SwingUtilities.invokeAndWait(() -> { long now=System.nanoTime(); audio.play(AudioManager.Sound.CLICK); audio.setMusicEnabled(true); audio.setEffectsEnabled(false); audio.close(); elapsed.set(System.nanoTime()-now); });
            check(elapsed.get()<100_000_000L,"Play, mute and close do not block EDT even during slow preload");
        } finally { release.countDown(); close(audio); }
        check(late.closes.get()==1,"Clip returned after shutdown is disposed");
    }
    private static void device() throws Exception {
        AudioManager audio=new AudioManager();
        try {
            AudioManager.Report initial=settled(audio); System.out.println("DEVICE loaded="+initial.loaded()+" unavailable="+initial.failed());
            if(initial.loaded()==0) { System.out.println("SKIP DEVICE: no available mixer (asset/scheduler tests still ran)"); return; }
            System.out.println("DEVICE opened "+initial.loaded()+" of 15 cached clips; device capability is informational");
            int maximumFrames=0;
            for(AudioManager.Sound sound:AudioManager.Sound.values()) if(!sound.isMusic()) {
                audio.stopTransient(); audio.play(sound); Thread.sleep(55);
                maximumFrames=Math.max(maximumFrames,settled(audio).framesAdvanced()); Thread.sleep(125);
            }
            check(maximumFrames>0,"Native clips advance playback frame positions");
            audio.setMusicEnabled(true); Thread.sleep(180); System.out.println("DEVICE ambient_running="+settled(audio).musicRunning());
            if(initial.loaded()==15) {
                check(settled(audio).musicRunning(),"Native lobby track loops");
                audio.setMusicScene(AudioManager.MusicScene.BATTLE); int start=settled(audio).framesAdvanced(); Thread.sleep(200);
                check(settled(audio).musicRunning() && settled(audio).framesAdvanced()>start,"Native battle track loops and advances frames");
                audio.setMusicScene(AudioManager.MusicScene.LOBBY); Thread.sleep(100);
                check(settled(audio).musicRunning(),"Native result/lobby track resumes after battle");
            } else System.out.println("SKIP DEVICE SCENES: incomplete mixer capacity; fake scene tests still ran");
            audio.setMusicEnabled(false); check(!settled(audio).musicRunning(),"Native music stops on toggle");
            System.out.println("DEVICE playback tested digitally; listening quality requires human review");
        } finally { close(audio); }
    }
    public static void main(String[] args) throws Exception {
        assets(); scheduling(); musicScenes(); absentAssets(); slowDevice(); if(Arrays.asList(args).contains("--device")) device();
        System.out.println("Audio checks passed: "+checks);
    }
}
