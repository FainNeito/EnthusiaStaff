package net.enthusia.staff.paper.visibility;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class VanishNoclipControllerTest {
    @Test void creativeStaffFullVanishUsesServerPhysicsAndClientSpectatorPresentation() {
        FakePlayer s=new FakePlayer(GameMode.CREATIVE,false); FakeClientModes c=new FakeClientModes(true);
        VanishNoclipController x=new VanishNoclipController(c);
        assertTrue(x.reconcile(s.player(),true)); assertTrue(s.noPhysics);
        assertEquals(List.of(GameMode.SPECTATOR),c.presented);
    }
    @Test void spectatorStaffFullVanishNeedsNoInternalClientPacket() {
        FakePlayer s=new FakePlayer(GameMode.SPECTATOR,true); FakeClientModes c=new FakeClientModes(false);
        VanishNoclipController x=new VanishNoclipController(c);
        assertTrue(x.reconcile(s.player(),true)); assertTrue(s.noPhysics); assertTrue(c.presented.isEmpty());
    }
    @Test void disablingVanishRestoresOwnedCreativePhysicsAndPresentation() {
        FakePlayer s=new FakePlayer(GameMode.CREATIVE,false); FakeClientModes c=new FakeClientModes(true);
        VanishNoclipController x=new VanishNoclipController(c);
        assertTrue(x.reconcile(s.player(),true)); assertTrue(x.reconcile(s.player(),false));
        assertFalse(s.noPhysics); assertEquals(List.of(GameMode.SPECTATOR,GameMode.CREATIVE),c.presented);
        assertEquals(1,s.inventoryUpdates);
    }
    @Test void gameModeReconciliationMakesStaffExitSnapshotAuthoritative() {
        FakePlayer s=new FakePlayer(GameMode.CREATIVE,false); FakeClientModes c=new FakeClientModes(true);
        VanishNoclipController x=new VanishNoclipController(c);
        assertTrue(x.reconcile(s.player(),true)); s.gameMode=GameMode.SPECTATOR;
        x.gameModeChanged(s.id,GameMode.SPECTATOR,true); assertTrue(x.reconcile(s.player(),true));
        assertTrue(x.reconcile(s.player(),false)); assertTrue(s.noPhysics);
    }
    @Test void repeatedRestoreTeleportAndHandoffReconciliationDoNotReplaceBaseline() {
        FakePlayer s=new FakePlayer(GameMode.CREATIVE,false); FakeClientModes c=new FakeClientModes(true);
        VanishNoclipController x=new VanishNoclipController(c);
        assertTrue(x.reconcile(s.player(),true)); assertTrue(x.reconcile(s.player(),true));
        assertTrue(x.reconcile(s.player(),true)); assertTrue(x.reconcile(s.player(),false)); assertFalse(s.noPhysics);
    }
    @Test void normalNonVanishedPlayIsNotMutated() {
        FakePlayer s=new FakePlayer(GameMode.SURVIVAL,false); FakeClientModes c=new FakeClientModes(true);
        VanishNoclipController x=new VanishNoclipController(c);
        assertTrue(x.reconcile(s.player(),false)); assertFalse(s.noPhysics); assertTrue(c.presented.isEmpty());
        assertEquals(0,s.inventoryUpdates);
    }
    @Test void incompatibleCreativeRuntimeFailsClosedAndRestoresPhysics() {
        FakePlayer s=new FakePlayer(GameMode.CREATIVE,false); FakeClientModes c=new FakeClientModes(false);
        VanishNoclipController x=new VanishNoclipController(c);
        assertFalse(x.canEnable(s.player())); assertFalse(x.reconcile(s.player(),true)); assertFalse(s.noPhysics);
    }

    private static final class FakeClientModes implements VanishClientGameModeAdapter {
        private final boolean available; private final List<GameMode> presented=new ArrayList<>();
        FakeClientModes(boolean available){this.available=available;}
        @Override public boolean available(){return available;}
        @Override public String unavailableReason(){return available?"":"test runtime mismatch";}
        @Override public boolean present(Player player,GameMode gameMode){
            if(!available)return false; presented.add(gameMode); return true;
        }
    }
    private static final class FakePlayer {
        private final UUID id=UUID.randomUUID(); private GameMode gameMode; private boolean noPhysics;
        private int inventoryUpdates;
        FakePlayer(GameMode gameMode,boolean noPhysics){this.gameMode=gameMode;this.noPhysics=noPhysics;}
        Player player(){
            return (Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},
                    (proxy,method,args)->switch(method.getName()){
                        case "getUniqueId"->id; case "getGameMode"->gameMode; case "hasNoPhysics"->noPhysics;
                        case "setNoPhysics"->setNoPhysics(args); case "updateInventory"->updateInventory();
                        case "hashCode"->System.identityHashCode(proxy); case "equals"->proxy==args[0];
                        case "toString"->"FakePlayer["+id+"]"; default->defaultValue(method.getReturnType());
                    });
        }
        private Object setNoPhysics(Object[] args){noPhysics=(Boolean)args[0];return null;}
        private Object updateInventory(){inventoryUpdates++;return null;}
        private static Object defaultValue(Class<?> type){
            if(!type.isPrimitive())return null; if(type==boolean.class)return false; if(type==char.class)return '\0';
            return 0;
        }
    }
}
