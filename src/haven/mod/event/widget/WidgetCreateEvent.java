package haven.mod.event.widget;

import haven.mod.event.Event;

public abstract class WidgetCreateEvent extends Event {

    protected int id;
    protected String type;
    protected int parent;
    protected Object[] pargs;
    protected Object[] cargs;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public int getParent() {
        return parent;
    }

    public void setParent(int parent) {
        this.parent = parent;
    }

    public Object[] getParentArgs() {
        return pargs;
    }

    public void setParentArgs(Object[] pargs) {
        this.pargs = pargs;
    }

    public Object[] getChildArgs() { return cargs; }

    public void setChildArgs(Object[] cargs) {
        this.cargs = cargs;
    }
}
