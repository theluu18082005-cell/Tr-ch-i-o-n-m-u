package vn.ptit.colorgame;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.*;
import java.util.List;
import java.util.concurrent.*;

/** Real UI handlers with deterministic silent voices; no hardware requirement. */
public final class ClientAudioTest {
    private static Object field(GameClient c,String name) throws Exception { Field f=GameClient.class.getDeclaredField(name); f.setAccessible(true); return f.get(c); }
    private static void edt(Runnable action) throws Exception { SwingUtilities.invokeAndWait(action); }
    private static void message(GameClient c,String... fields) throws Exception { edt(() -> c.onMessage(fields)); }
    private static void click(GameClient c,String name) throws Exception { edt(() -> { try { ((JButton)field(c,name)).doClick(0); } catch(Exception e) { throw new RuntimeException(e); } }); }
    private static void check(boolean condition,String text) { AudioManagerTest.check(condition,text); }
    @SuppressWarnings("unchecked")
    private static void toggle(GameClient c,String name,boolean target) throws Exception {
        edt(() -> { try { List<JCheckBox> boxes=(List<JCheckBox>)field(c,name); if(boxes.get(0).isSelected()!=target) boxes.get(0).doClick(0); check(boxes.size()==1 && boxes.stream().allMatch(b -> b.isSelected()==target),"Single shared settings state: "+name+"="+target); } catch(Exception e) { throw new RuntimeException(e); } });
    }
    private static void room(GameClient c,String id,String player) throws Exception { message(c,"ROOM",id,"alice","bob"); message(c,"TURN",id,"1",player,"30000"); }
    public static void main(String[] args) throws Exception {
        AudioManagerTest.FakeLoader loader=new AudioManagerTest.FakeLoader(false); AudioManager audio=new AudioManager(loader);
        AudioManagerTest.settled(audio); GameClient[] ref=new GameClient[1]; edt(() -> ref[0]=new GameClient(audio)); GameClient c=ref[0];
        try {
            check(((List<?>)field(c,"settingsButtons")).size()==4,"Four screens open the same settings panel");
            toggle(c,"soundSettings",false); toggle(c,"musicSettings",true);
            check(!audio.effectsEnabled() && AudioManagerTest.settled(audio).musicRunning(),"Music and sound switches independent in client");
            toggle(c,"soundSettings",true); toggle(c,"musicSettings",false);
            edt(() -> { try { ((JSlider)field(c,"musicVolume")).setValue(0); } catch(Exception e) { throw new RuntimeException(e); } });
            toggle(c,"musicSettings",true); check(!AudioManagerTest.settled(audio).musicRunning(),"Music volume zero stops playback");
            edt(() -> { try { ((JSlider)field(c,"musicVolume")).setValue(45); } catch(Exception e) { throw new RuntimeException(e); } });
            check(AudioManagerTest.settled(audio).musicRunning(),"Raising volume resumes enabled music");
            toggle(c,"musicSettings",false);
            click(c,"showRegister"); AudioManagerTest.settled(audio); check(loader.starts(AudioManager.Sound.CLICK)==1,"Navigation button has a short click cue");
            message(c,"AUTH","alice","Alice","5","5","8");
            message(c,"INVITE","invite-1","bob","30"); message(c,"INVITE","invite-1","bob","29");
            AudioManagerTest.settled(audio); check(loader.starts(AudioManager.Sound.INVITE)==1,"Repeated invitation update does not repeat incoming cue");
            click(c,"rejectInvite"); AudioManagerTest.settled(audio); check(loader.starts(AudioManager.Sound.REJECT)==1,"Reject invitation uses rejection cue");
            message(c,"INVITE_CLOSED","invite-1","REJECTED"); message(c,"INVITE","invite-2","bob","30"); click(c,"acceptInvite");
            AudioManagerTest.settled(audio); check(loader.starts(AudioManager.Sound.ACCEPT)==1,"Accept invitation uses confirmation cue");
            toggle(c,"musicSettings",true);
            room(c,"12345678-audio","alice"); AudioManagerTest.settled(audio);
            check(audio.musicScene()==AudioManager.MusicScene.BATTLE && loader.starts(AudioManager.Sound.BATTLE_MUSIC)==1 && !loader.voices.get(AudioManager.Sound.AMBIENT).running,"Entering real client room selects only battle music");
            message(c,"TICK","12345678-audio","1","alice","29900");
            AudioManagerTest.settled(audio); check(loader.starts(AudioManager.Sound.TURN)==1,"TURN then TICK plays turn cue once");
            check(loader.starts(AudioManager.Sound.BATTLE_MUSIC)==1,"TURN/TICK do not restart battle loop");
            click(c,"submit"); AudioManagerTest.settled(audio); check(loader.starts(AudioManager.Sound.SUBMIT)==0,"Incomplete guess cannot emit submit cue");
            edt(() -> { c.chooseColor(0); c.chooseColor(0); }); AudioManagerTest.settled(audio);
            check(loader.starts(AudioManager.Sound.ORB)==1,"Valid orb selection sounds once; duplicate stays silent");
            edt(() -> { try { JButton[] slots=(JButton[])field(c,"slots"); slots[0].doClick(0); slots[1].doClick(0); } catch(Exception e) { throw new RuntimeException(e); } });
            AudioManagerTest.settled(audio); check(loader.starts(AudioManager.Sound.SWAP)==1,"Slot interaction emits throttled swap cue");
            edt(() -> { for(int i=1;i<6;i++) c.chooseColor(i); }); click(c,"submit"); AudioManagerTest.settled(audio);
            check(loader.starts(AudioManager.Sound.SUBMIT)==1 && (boolean)field(c,"submitted"),"Complete guess retains submission behavior and emits submit cue");
            message(c,"TURN","12345678-audio","2","bob","4500"); int before=loader.starts(AudioManager.Sound.ORB);
            edt(() -> c.chooseColor(2)); AudioManagerTest.settled(audio); check(loader.starts(AudioManager.Sound.ORB)==before,"Opponent turn cannot trigger orb action or cue");
            message(c,"TURN","12345678-audio","3","alice","4900"); message(c,"TICK","12345678-audio","3","alice","4800");
            AudioManagerTest.settled(audio); check(loader.starts(AudioManager.Sound.WARNING)==1,"Repeated tick in fifth second warns once");
            Thread.sleep(800); message(c,"TICK","12345678-audio","3","alice","3900"); AudioManagerTest.settled(audio);
            check(loader.starts(AudioManager.Sound.WARNING)==2,"Next countdown second can emit next warning");
            message(c,"MOVE","12345678-audio","3","alice","","0","TIMEOUT"); message(c,"MOVE","12345678-audio","3","alice","","0","TIMEOUT");
            AudioManagerTest.settled(audio); check(loader.starts(AudioManager.Sound.TIMEOUT)==1,"Repeated timeout message sounds once");
            message(c,"RESULT","12345678-audio","alice","LEFT"); message(c,"RESULT","12345678-audio","alice","LEFT"); AudioManagerTest.settled(audio);
            check(audio.musicScene()==AudioManager.MusicScene.LOBBY && AudioManagerTest.settled(audio).musicRunning() && !loader.voices.get(AudioManager.Sound.BATTLE_MUSIC).running,"Result returns to calm music while victory cue plays");
            check(loader.starts(AudioManager.Sound.PEER_LEFT)==1 && loader.starts(AudioManager.Sound.VICTORY)==1,"Peer departure and victory cues fire once per result");
            room(c,"12345678-loss","bob"); AudioManagerTest.settled(audio);
            check(audio.musicScene()==AudioManager.MusicScene.BATTLE && loader.starts(AudioManager.Sound.BATTLE_MUSIC)==2,"New room/rematch returns to battle music");
            message(c,"RESULT","12345678-loss","bob","SOLVED"); AudioManagerTest.settled(audio);
            check(loader.starts(AudioManager.Sound.DEFEAT)==1 && !(boolean)field(c,"active"),"Defeat cue preserves match completion");
            toggle(c,"musicSettings",false); toggle(c,"soundSettings",false); room(c,"12345678-mute","alice"); int count=AudioManagerTest.settled(audio).played();
            edt(() -> { for(int i=0;i<6;i++) c.chooseColor(i); }); click(c,"submit");
            check(AudioManagerTest.settled(audio).played()==count && (boolean)field(c,"submitted"),"Muted audio keeps every gameplay action working");
            message(c,"LOBBY"); check(audio.musicScene()==AudioManager.MusicScene.LOBBY && !AudioManagerTest.settled(audio).musicRunning(),"Returning to lobby preserves music mute");
            room(c,"12345678-close","alice");
            edt(() -> { try { Method method=GameClient.class.getDeclaredMethod("logout"); method.setAccessible(true); method.invoke(c); } catch(Exception e) { throw new RuntimeException(e); } });
            check(audio.musicScene()==AudioManager.MusicScene.LOBBY && !AudioManagerTest.settled(audio).musicRunning(),"Logout returns music scene to login without overriding mute");
            edt(() -> GameTheme.setReducedMotion(true)); check(!audio.effectsEnabled() && !audio.musicEnabled(),"Reduced effects does not override audio preferences");
        } finally { edt(() -> { c.shutdown(); GameTheme.setReducedMotion(false); }); audio.whenClosed().get(6,TimeUnit.SECONDS); }
        check(loader.voices.values().stream().allMatch(v -> v.closes.get()==1),"Client shutdown disposes audio worker resources");
        System.out.println("Client audio checks passed");
    }
}
