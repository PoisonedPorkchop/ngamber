package haven.mod;

import haven.*;
import haven.mod.chat.CustomChannel;
import haven.mod.chat.CustomChat;
import haven.pathfinder.PFListener;
import haven.pathfinder.Pathfinder;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static haven.OCache.posres;

/**
 * Class that contains convenience methods for carrying out autonomous actions in Haven.
 */
public class ModAction {

    /**
     * Move to a location in the world.
     * @param location Location to move to.
     */
    public void moveTo(Coord location)
    {
        HavenPanel.lui.wdgmsg(getGUI().map,"click", Coord.z, location, 1, 0);
    }

    /**
     * Gets all map objects. Important for finding targets.
     * @return List of all current Game Objects. Be careful not to reference these when they have unloaded.
     */
    public ArrayList<Gob> getMapObjects()
    {
        ArrayList<Gob> gobs = new ArrayList<>();
        for(Gob gob : getGUI().map.glob.oc)
            gobs.add(gob);
        return gobs;
    }

    public Map<Coord, MCache.Grid> getGrids()
    {
        return getGUI().map.glob.map.getGrids();
    }

    public MCache.Grid getCurrentGrid()
    {
        ArrayList<Coord> coords = new ArrayList<>();
        for(Map.Entry<Coord, MCache.Grid> entry : getGrids().entrySet())
            coords.add(entry.getKey());
        int lowX = coords.get(0).x;
        int lowY = coords.get(0).y;
        int highX = coords.get(0).x;
        int highY = coords.get(0).y;

        for(Coord coord : coords) {
            lowX = Math.min(lowX, coord.x);
            highX = Math.max(highX, coord.x);
            lowY = Math.min(lowY, coord.y);
            highY = Math.max(highY, coord.y);
        }

        for(Map.Entry<Coord, MCache.Grid> entry : getGrids().entrySet())
            if(entry.getKey().x == ((lowX + highX)/2) && entry.getKey().y == ((lowY + highY)/2))
                return entry.getValue();
        return null;
    }

    public String identifyTile(int id)
    {
        return getGUI().map.glob.map.tileset(id).getres().name;
    }

    public Coord getLocationOfTile(Coord tile, MCache.Grid grid)
    {
        Mod.debug("Player start: " + getLocationOfGob(getPlayer()));
        int playerx = getLocationOfGob(getPlayer()).x + 24000;
        int playery = getLocationOfGob(getPlayer()).y + 24000;

        Mod.debug("newplayerx: " + playerx + " newplayery: " + playery);

        int modx = 100000 + (playerx % 100000);
        int mody = 100000 + (playery % 100000);

        int startx = (playerx - modx) - 24000;
        int starty = (playery - mody) - 24000;

        startx += ((tile.x * 1000)+500);
        starty += ((tile.y * 1000)+500);

        startx += (grid.gc.x * 100000);
        starty += (grid.gc.y * 100000);

        return new Coord(startx,starty);
    }

    /**
     * Get the current player.
     * @return The player's Game Object.
     */
    public Gob getPlayer()
    {
        return getGUI().map.player();
    }

    /**
     * Right click an object. Can be used to interact with objects such as cupboards, dropped items, plants etc.
     * @param gob Game object to right click.
     */
    public void rightClick(Gob gob)
    {
        getGUI().map.wdgmsg("click", gob.sc, getLocationOfGob(gob), 3, 0, 0, (int) gob.id, getLocationOfGob(gob), 0, -1);
    }

    public Pathfinder pathfindTo(Coord location)
    {
        return getGUI().map.pfLeftClick(getBaseCoordFromWorldCoord(location), null);
    }

    public Pathfinder pathfindInteract(Gob gob)
    {
        return getGUI().map.pfRightClick(gob,-1,3,0, null);
    }

    public void waitForPathfinding(Pathfinder pathfinder, long milliseconds)
    {
        final boolean[] waiting = {true};
        pathfinder.addListener(new PFListener() {
            @Override
            public void pfDone(Pathfinder pathfinder) {
                waiting[0] = false;
            }
        });
        while (waiting[0])
            try {
                Thread.sleep(milliseconds);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
    }

    public void waitForPathfinding(Pathfinder pathfinder, long milliseconds, long timeout) throws InterruptedException {
        final boolean[] waiting = {true};
        pathfinder.addListener(new PFListener() {
            @Override
            public void pfDone(Pathfinder pathfinder) {
                waiting[0] = false;
            }
        });
        long currentTime = System.currentTimeMillis();
        while (waiting[0] && (currentTime - System.currentTimeMillis()) < timeout)
            Thread.sleep(milliseconds);
    }

    /**
     * Right click a location in the world. Primarily for placing carried objects.
     * @param location Location to be right clicked.
     */
    public void rightClick(Coord location)
    {
        getGUI().map.wdgmsg("click", Coord.z, location, 3, 0);
    }

    /**
     * Picks up an object.
     * @param gob Game Object to be picked up.
     */
    public void pickUpObject(Gob gob)
    {
        getGUI().menu.wdgmsg("act", "carry");
        getGUI().map.wdgmsg("click", Coord.z, getLocationOfGob(gob), 1, 0, 0, (int) gob.id, getLocationOfGob(gob), 0, -1);
    }

    public int getFreeSpaceInInventory()
    {
        return getFreeSpaceInInventory(getMainInventory());
    }

    public int getFreeSpaceInInventory(Inventory inv) {
        return inv.getFreeSpace();
    }

    public Inventory getMainInventory()
    {
        return getGUI().maininv;
    }

    public WItem getItem(String itemName)
    {
        return getItem(itemName, getMainInventory());
    }

    public WItem getItem(String itemName, Inventory inv) {
        return inv.getItemPartial(itemName);
    }

    public List<WItem> getAllItems(Inventory inv)
    {
        return inv.getAllItems();
    }

    public List<WItem> getItems(String itemName)
    {
        return getItems(itemName, getGUI().maininv);
    }

    public List<WItem> getItems(String itemName, Inventory inv)
    {
        return inv.getItemsPartial(itemName);
    }

    public void dropItem(GItem item)
    {
        item.wdgmsg("drop", new Coord(0,0));
    }

    public void dropItemInHand(boolean shift)
    {
        getGUI().map.wdgmsg("drop", new Coord(0,0), getLocationOfGob(getPlayer()), 0);
    }

    public void transferItem(GItem item)
    {
        item.wdgmsg("transfer", new Coord(0,0));
    }

    /**
     * Gets the current world location of a Game Object.
     * @param gob Game Object to get the location of.
     * @return Coord that is the location of the game object in world terms.
     */
    public Coord getLocationOfGob(Gob gob)
    {
        return gob.rc.floor(posres);
    }

    private Coord getBaseCoordFromWorldCoord(Coord worldCoord)
    {
        return worldCoord.mul(posres).floor();
    }

    /**
     * Gets the gui. Be careful, as it might not be active.
     * @return Current GameUI
     */
    public GameUI getGUI()
    {
        return HavenPanel.lui.root.findchild(GameUI.class);
    }

    public ArrayList<ChatUI.MultiChat> getPublicChats()
    {
        ArrayList<ChatUI.MultiChat> selectedChannels = new ArrayList<>();
        for(ChatUI.Channel channel : getChannels())
            if(channel instanceof ChatUI.MultiChat)
                selectedChannels.add((ChatUI.MultiChat) channel);
        return selectedChannels;
    }

    public ChatUI.PartyChat getPartyChat()
    {
        for(ChatUI.Channel channel : getChannels())
            if(channel instanceof ChatUI.PartyChat)
                return (ChatUI.PartyChat) channel;
        return null;
    }

    public ArrayList<ChatUI.PrivChat> getPrivateChats()
    {
        ArrayList<ChatUI.PrivChat> selectedChannels = new ArrayList<>();
        for(ChatUI.Channel channel : getChannels())
            if(channel instanceof ChatUI.PrivChat)
                selectedChannels.add((ChatUI.PrivChat) channel);
        return selectedChannels;
    }

    public ArrayList<ChatUI.Channel> getChannels()
    {
        return getGUI().chat.getChannels();
    }

    public ChatUI.Channel getSystemLog()
    {
        for(ChatUI.Channel channel : getChannels())
            if(channel.name().equals("System"))
                return channel;
        return null;
    }

    public ChatUI.Channel getChannelByName(String name)
    {
        for(ChatUI.Channel channel : getChannels())
            if(channel.name().equals(name))
                return channel;
        return null;
    }

    public void printToSystem(String msg, Color color)
    {
        printToChannel(getSystemLog(), msg, color);
    }

    public void printToChannel(ChatUI.Channel channel, String msg, Color color)
    {
        channel.append(msg, color);
    }

    public void sendMessage(ChatUI.MultiChat chat, String message)
    {
        chat.wdgmsg("msg", message);
    }

    public void sendMessage(ChatUI.PrivChat chat, String message)
    {
        chat.wdgmsg("msg", message);
    }

    public CustomChannel addCustomChannel(CustomChannel channel)
    {
        if(new Mod().getAPI().addCustomChannel(channel))
            getGUI().chat.add(channel);
        return channel;
    }

    public void removeCustomChannel(CustomChannel remove)
    {
        if(new Mod().getAPI().removeCustomChannel(remove))
            remove.reqdestroy();
    }

    public CustomChat addCustomChat(CustomChat channel)
    {
        if(new Mod().getAPI().addCustomChat(channel))
            getGUI().chat.add(channel);
        return channel;
    }

    public void removeCustomChat(CustomChat remove)
    {
        if(new Mod().getAPI().removeCustomChat(remove))
            remove.reqdestroy();
    }

    public ArrayList<Gob> getAggrod()
    {
        ArrayList<Gob> aggrodGobs = new ArrayList<>();
        synchronized (getGUI()) {
            synchronized (getGUI().fv) {
                synchronized (getGUI().fv.lsrel) {
                    synchronized (getGUI().ui.sess.glob.oc) {
                        if (getGUI().fv != null) {
                            if (getGUI().fv.lsrel != null) {
                                for (Fightview.Relation relation : getGUI().fv.lsrel) {
                                    aggrodGobs.add(getGUI().ui.sess.glob.oc.getgob(relation.gobid));
                                }
                                return aggrodGobs;
                            }
                        }
                    }
                }
            }
        }
        return aggrodGobs;
    }

    public void aggroAllNearbyEnemies() {
        if (new Mod().actions().getGUI() != null) {
            synchronized (new Mod().actions().getGUI()) {
                synchronized (new Mod().actions().getGUI().ui) {
                    synchronized (new Mod().actions().getGUI().ui.sess) {
                        synchronized (new Mod().actions().getGUI().ui.sess.glob) {
                            synchronized (new Mod().actions().getGUI().ui.sess.glob.oc) {
                                synchronized (new Mod().actions().getGUI().map) {
                                    double gobclsdist = Double.MAX_VALUE;
                                    for (Gob gob : new Mod().actions().getGUI().ui.sess.glob.oc) {
                                        try {
                                            Resource res = gob.getres();
                                            if(res != null) {
                                                synchronized (res) {
                                                    synchronized (new Mod().actions().getPlayer()) {
                                                        if ("body".equals(res.basename()) && gob.id != new Mod().actions().getPlayer().id) {
                                                            if (!gob.isFriend()) {
                                                                double dist = new Mod().actions().getPlayer().rc.dist(gob.rc);
                                                                System.out.println("Found enemy: " + gob.id + "-" + gob.sc.x + "-" + gob.sc.y + "-" + dist);

                                                                if (dist < 340) {
                                                                    new Mod().actions().getGUI().wdgmsg("act", "aggro");
                                                                    new Mod().actions().getGUI().map.wdgmsg("click", gob.sc, new Mod().actions().getLocationOfGob(gob), 1, 0, 0, (int) gob.id, new Mod().actions().getLocationOfGob(gob), 0, 0);
                                                                    Gob pl = new Mod().actions().getPlayer();
                                                                    new Mod().actions().getGUI().map.wdgmsg("click", pl.sc, pl.rc.floor(posres), 3, 0);
                                                                    //getGUI().wdgmsg("click", gob.sc, Coord.z, 1, 0, 0, (int) gob.id, gob.rc.floor(posres), 0, 0);
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        } catch (Loading l) {
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public void aggroGob(Gob gob)
    {
        synchronized (getGUI()) {
            synchronized (getGUI().map) {
                synchronized (gob) {
                    new Mod().actions().getGUI().wdgmsg("act", "aggro");
                    new Mod().actions().getGUI().map.wdgmsg("click", gob.sc, new Mod().actions().getLocationOfGob(gob), 1, 0, 0, (int) gob.id, new Mod().actions().getLocationOfGob(gob), 0, 0);
                    Gob pl = new Mod().actions().getPlayer();
                    new Mod().actions().getGUI().map.wdgmsg("click", pl.sc, pl.rc.floor(posres), 3, 0);
                }
            }
        }
    }

    public void itemInteract(WItem item)
    {
        getGUI().ui.lcc = item.rootpos();
        item.item.wdgmsg("iact", item.c, 0);
    }

    public void login(LoginScreen screen, String name, String pass)
    {
        screen.wdgmsg("forget");
        screen.wdgmsg("login", new AuthClient.NativeCred(name,pass),false);
    }

}
