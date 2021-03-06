/*
 *  This file is part of the Haven & Hearth game client.
 *  Copyright (C) 2009 Fredrik Tolf <fredrik@dolda2000.com>, and
 *                     Björn Johannessen <johannessen.bjorn@gmail.com>
 *
 *  Redistribution and/or modification of this file is subject to the
 *  terms of the GNU Lesser General Public License, version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  Other parts of this source tree adhere to other copying
 *  rights. Please see the file `COPYING' in the root directory of the
 *  source tree for details.
 *
 *  A copy the GNU Lesser General Public License is distributed along
 *  with the source tree of which this file is a part in the file
 *  `doc/LPGL-3'. If it is missing for any reason, please see the Free
 *  Software Foundation's website at <http://www.fsf.org/>, or write
 *  to the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 *  Boston, MA 02111-1307 USA
 */

package haven;

import haven.mod.Mod;
import haven.mod.event.UIMessageEvent;
import haven.mod.event.widget.*;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.*;

public class UI {
    public RootWidget root;
    final private LinkedList<Grab> keygrab = new LinkedList<Grab>(), mousegrab = new LinkedList<Grab>();
    public Map<Integer, Widget> widgets = new TreeMap<Integer, Widget>();
    public Map<Widget, Integer> rwidgets = new HashMap<Widget, Integer>();
    Receiver rcvr;
    public Coord mc = Coord.z, lcc = Coord.z;
    public Session sess;
    public boolean modshift, modctrl, modmeta, modsuper;
    public int keycode;
    public Object lasttip;
    long lastevent, lasttick;
    public Widget mouseon;
    public Console cons = new WidgetConsole();
    private Collection<AfterDraw> afterdraws = new LinkedList<AfterDraw>();
    public final ActAudio audio = new ActAudio();

    {
        lastevent = lasttick = System.currentTimeMillis();
    }

    public interface Receiver {
        void rcvmsg(int widget, String msg, Object... args);
    }

    public interface Runner {
        Session run(UI ui) throws InterruptedException;
    }

    public interface AfterDraw {

    }

    private class WidgetConsole extends Console {
        {
            setcmd("q", (cons1, args) -> HackThread.tg().interrupt());
            setcmd("lo", (cons1, args) -> sess.close());
            setcmd("kbd", (cons1, args) -> {
                Config.zkey = args[1].toString().equals("z") ? KeyEvent.VK_Y : KeyEvent.VK_Z;
                Utils.setprefi("zkey", Config.zkey);
            });
            setcmd("charter", (cons1, args) -> CharterList.addCharter(args[1]));
        }

        private void findcmds(Map<String, Command> map, Widget wdg) {
            if (wdg instanceof Directory) {
                Map<String, Command> cmds = ((Directory) wdg).findcmds();
                synchronized (cmds) {
                    map.putAll(cmds);
                }
            }
            for (Widget ch = wdg.child; ch != null; ch = ch.next)
                findcmds(map, ch);
        }

        public Map<String, Command> findcmds() {
            Map<String, Command> ret = super.findcmds();
            findcmds(ret, root);
            return (ret);
        }
    }

    @SuppressWarnings("serial")
    public static class UIException extends RuntimeException {
        public String mname;
        public Object[] args;

        public UIException(String message, String mname, Object... args) {
            super(message);
            this.mname = mname;
            this.args = args;
        }
    }

    public UI(Coord sz, Session sess) {
        root = new RootWidget(this, sz);
        widgets.put(0, root);
        rwidgets.put(root, 0);
        this.sess = sess;
    }

    public void setreceiver(Receiver rcvr) {
        this.rcvr = rcvr;
    }

    public void bind(Widget w, int id) {
        widgets.put(id, w);
        rwidgets.put(w, id);
    }

    public void drawafter(AfterDraw ad) {
        synchronized (afterdraws) {
            afterdraws.add(ad);
        }
    }

    public void tick() {
        long now = System.currentTimeMillis();
        root.tick((now - lasttick) / 1000.0);
        lasttick = now;
    }

    public void newwidget(int id, String type, int parent, Object[] pargs, Object... cargs) throws InterruptedException {

        if(new WidgetPreCreateEvent(id, type, parent, pargs, cargs).callAndGetCancelled())
            return;

        if (Config.quickbelt && type.equals("wnd") && cargs[1].equals("Belt")) {
            type = "wnd-belt";
            pargs[1] = Utils.getprefc("Belt_c", new Coord(550, HavenPanel.h - 160));
        }

        Widget.Factory f = Widget.gettype2(type);
        synchronized (this) {
            Widget pwdg = widgets.get(parent);
            if (pwdg == null)
                throw (new UIException("Null parent widget " + parent + " for " + id, type, cargs));

            Widget wdg = pwdg.makechild(f, pargs, cargs);

            GameUI gui = wdg.gameui();
            if (wdg instanceof Window)
                processWindowCreation(id, gui, (Window)wdg);
            else if (pwdg instanceof Window)
                processWindowContent(parent, gui, (Window)pwdg, wdg);

            if (type.equals("wnd-belt")) {
                gui.getequipory().beltWndId = id;
            }

            bind(wdg, id);

            new WidgetPostCreateEvent(id, type, parent, pargs, wdg, cargs).call();

            // drop everything except water containers if in area mining mode
            if (Config.dropore && gui != null && gui.map != null && gui.map.areamine != null && wdg instanceof GItem) {
                if (gui.maininv == pwdg) {
                    final GItem itm = (GItem) wdg;
                    Defer.later(new Defer.Callable<Void>() {
                        public Void call() {
                            try {
                                String name = itm.resource().name;
                                if (!name.endsWith("waterflask") && !name.endsWith("waterskin") && !name.endsWith("pebble-gold"))
                                    itm.wdgmsg("drop", Coord.z);
                            } catch (Loading e) {
                                Defer.later(this);
                            }
                            return null;
                        }
                    });
                }
            }
        }
    }

    private void processWindowContent(long wndid, GameUI gui, Window pwdg, Widget wdg) {
        String cap = pwdg.origcap;
        if (gui != null && gui.livestockwnd.pendingAnimal != null && gui.livestockwnd.pendingAnimal.wndid == wndid) {
            if (wdg instanceof TextEntry)
                gui.livestockwnd.applyName(wdg);
            else if (wdg instanceof Label)
                gui.livestockwnd.applyAttr(cap, wdg);
            else if (wdg instanceof Avaview)
                gui.livestockwnd.applyId(wdg);
        } else if (wdg instanceof ISBox && cap.equals("Stockpile")) {
            TextEntry entry = new TextEntry(40, "") {
                @Override
                public boolean keydown(KeyEvent e) {
                    return !(e.getKeyCode() >= KeyEvent.VK_F1 && e.getKeyCode() <= KeyEvent.VK_F12);
                }

                @Override
                public boolean type(char c, KeyEvent ev) {
                    if (c >= KeyEvent.VK_0 && c <= KeyEvent.VK_9 && buf.line.length() < 2 || c == '\b') {
                        return buf.key(ev);
                    } else if (c == '\n') {
                        try {
                            int count = Integer.parseInt(dtext());
                            for (int i = 0; i < count; i++)
                                wdg.wdgmsg("xfer");
                            return true;
                        } catch (NumberFormatException e) {
                        }
                    }
                    return false;
                }
            };
            Button btn = new Button(65, "Take") {
                @Override
                public void click() {
                    try {
                        String cs = entry.dtext();
                        int count = cs.isEmpty() ? 1 : Integer.parseInt(cs);
                        for (int i = 0; i < count; i++)
                            wdg.wdgmsg("xfer");
                    } catch (NumberFormatException e) {
                    }
                }
            };
            pwdg.add(btn, new Coord(0, wdg.sz.y + 5));
            pwdg.add(entry, new Coord(btn.sz.x + 5, wdg.sz.y + 5 + 2));
        }
    }

    private void processWindowCreation(long wdgid, GameUI gui, Window wdg) {
        String cap = wdg.origcap;
        if (cap.equals("Charter Stone") || cap.equals("Sublime Portico")) {
            // show secrets list only for already built chartes/porticos
            if (wdg.wsz.y >= 80) {
                wdg.add(new CharterList(150, 5), new Coord(0, 50));
                wdg.presize();
            }
        } else if (gui != null && gui.livestockwnd != null && gui.livestockwnd.getAnimalPanel(cap) != null) {
            gui.livestockwnd.initPendingAnimal(wdgid, cap);
        }
    }

    public abstract class Grab {
        public final Widget wdg;

        public Grab(Widget wdg) {
            this.wdg = wdg;
        }

        public abstract void remove();
    }

    public Grab grabmouse(Widget wdg) {
        if (wdg == null) throw (new NullPointerException());
        Grab g = new Grab(wdg) {
            public void remove() {
                mousegrab.remove(this);
            }
        };
        mousegrab.addFirst(g);
        return (g);
    }

    public Grab grabkeys(Widget wdg) {
        //WidgetGrabKeysEvent
        WidgetGrabKeysEvent widgetGrabKeysEvent = new WidgetGrabKeysEvent(wdg);
        new Mod().getAPI().callEvent(widgetGrabKeysEvent);
        if(widgetGrabKeysEvent.getCancelled())
            return null;
        //WidgetGrabKeysEvent

        if (wdg == null) throw (new NullPointerException());
        Grab g = new Grab(wdg) {
            public void remove() {
                keygrab.remove(this);
            }
        };
        keygrab.addFirst(g);
        return (g);
    }

    private void removeid(Widget wdg) {
        if (rwidgets.containsKey(wdg)) {
            int id = rwidgets.get(wdg);
            widgets.remove(id);
            rwidgets.remove(wdg);
        }
        for (Widget child = wdg.child; child != null; child = child.next)
            removeid(child);
    }

    public void destroy(Widget wdg) {
        //WidgetDestroyEvent
        WidgetDestroyEvent widgetDestroyEvent = new WidgetDestroyEvent(wdg);
        new Mod().getAPI().callEvent(widgetDestroyEvent);
        if(widgetDestroyEvent.getCancelled())
            return;
        //WidgetDestroyEvent

        for (Iterator<Grab> i = mousegrab.iterator(); i.hasNext(); ) {
            Grab g = i.next();
            if (g.wdg.hasparent(wdg))
                i.remove();
        }
        for (Iterator<Grab> i = keygrab.iterator(); i.hasNext(); ) {
            Grab g = i.next();
            if (g.wdg.hasparent(wdg))
                i.remove();
        }
        removeid(wdg);
        wdg.reqdestroy();
    }

    public void destroy(int id) {
        synchronized (this) {
            if (widgets.containsKey(id)) {
                Widget wdg = widgets.get(id);
                destroy(wdg);
            }
        }
    }

    public void wdgmsg(Widget sender, String msg, Object... args) {
        //WidgetMessageEventHandling
        WidgetMessageEvent widgetMessageEvent = new WidgetMessageEvent(sender, msg, args);
        new Mod().getAPI().callEvent(widgetMessageEvent);
        if(widgetMessageEvent.getCancelled())
            return;
        //WidgetMessageEventHandling

        int id;
        synchronized (this) {
            if (msg.endsWith("-identical"))
                return;
            if (!rwidgets.containsKey(sender))
                throw (new UIException("Wdgmsg sender (" + sender.getClass().getName() + ") is not in rwidgets", msg, args));
            id = rwidgets.get(sender);
        }
        if (rcvr != null)
            rcvr.rcvmsg(id, msg, args);
    }

    public void uimsg(int id, String msg, Object... args) {
        //UIMessageEventHandling
        UIMessageEvent uiMessageEvent = new UIMessageEvent(id,msg,args);
        new Mod().getAPI().callEvent(uiMessageEvent);
        if(uiMessageEvent.getCancelled())
            return;
        //UIMessageEventHandling

        synchronized (this) {
            Widget wdg = widgets.get(id);
            if (wdg != null)
                wdg.uimsg(msg.intern(), args);
            else
                throw (new UIException("Uimsg to non-existent widget " + id, msg, args));
        }
    }

    private void setmods(InputEvent ev) {
        int mod = ev.getModifiersEx();
        Debug.kf1 = modshift = (mod & InputEvent.SHIFT_DOWN_MASK) != 0;
        Debug.kf2 = modctrl = (mod & InputEvent.CTRL_DOWN_MASK) != 0;
        Debug.kf3 = modmeta = (mod & (InputEvent.META_DOWN_MASK | InputEvent.ALT_DOWN_MASK)) != 0;
    /*
    Debug.kf4 = modsuper = (mod & InputEvent.SUPER_DOWN_MASK) != 0;
	*/
    }

    private Grab[] c(Collection<Grab> g) {
        return (g.toArray(new Grab[0]));
    }

    public void type(KeyEvent ev) {
        setmods(ev);
        for (Grab g : c(keygrab)) {
            if (g.wdg.type(ev.getKeyChar(), ev))
                return;
        }
        if (!root.type(ev.getKeyChar(), ev))
            root.globtype(ev.getKeyChar(), ev);
    }

    public void keydown(KeyEvent ev) {
        setmods(ev);
        keycode = ev.getKeyCode();
        for (Grab g : c(keygrab)) {
            if (g.wdg.keydown(ev))
                return;
        }
        if (!root.keydown(ev))
            root.globtype((char) 0, ev);
    }

    public void keyup(KeyEvent ev) {
        setmods(ev);
        keycode = -1;
        for (Grab g : c(keygrab)) {
            if (g.wdg.keyup(ev))
                return;
        }
        root.keyup(ev);
    }

    private Coord wdgxlate(Coord c, Widget wdg) {
        return (c.add(wdg.c.inv()).add(wdg.parent.rootpos().inv()));
    }

    public boolean dropthing(Widget w, Coord c, Object thing) {
        if (w instanceof DropTarget) {
            if (((DropTarget) w).dropthing(c, thing))
                return (true);
        }
        for (Widget wdg = w.lchild; wdg != null; wdg = wdg.prev) {
            Coord cc = w.xlate(wdg.c, true);
            if (c.isect(cc, wdg.sz)) {
                if (dropthing(wdg, c.add(cc.inv()), thing))
                    return (true);
            }
        }
        return (false);
    }

    public void mousedown(MouseEvent ev, Coord c, int button) {
        setmods(ev);
        lcc = mc = c;
        for (Grab g : c(mousegrab)) {
            if (g.wdg.mousedown(wdgxlate(c, g.wdg), button))
                return;
        }
        root.mousedown(c, button);
    }

    public void mouseup(MouseEvent ev, Coord c, int button) {
        setmods(ev);
        mc = c;
        for (Grab g : c(mousegrab)) {
            if (g.wdg.mouseup(wdgxlate(c, g.wdg), button))
                return;
        }
        root.mouseup(c, button);
    }

    public void mousemove(MouseEvent ev, Coord c) {
        setmods(ev);
        mc = c;
        root.mousemove(c);
    }

    public void mousewheel(MouseEvent ev, Coord c, int amount) {
        setmods(ev);
        lcc = mc = c;
        for (Grab g : c(mousegrab)) {
            if (g.wdg.mousewheel(wdgxlate(c, g.wdg), amount))
                return;
        }
        root.mousewheel(c, amount);
    }

    public int modflags() {
        return ((modshift ? 1 : 0) |
                (modctrl ? 2 : 0) |
                (modmeta ? 4 : 0) |
                (modsuper ? 8 : 0));
    }

    public void destroy() {
        audio.clear();
    }
}
