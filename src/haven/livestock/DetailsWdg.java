package haven.livestock;


import haven.*;
import haven.Label;

import java.awt.*;
import java.util.Map;

import static haven.OCache.posres;

public class DetailsWdg extends Widget {
    public final static int HEIGHT = 25;
    private final Coord sepStart = new Coord(0, HEIGHT);
    private final Coord sepEnd = new Coord(800 - 40 - 11, HEIGHT);
    public Animal animal;
    private boolean hover = false;

    public DetailsWdg(Animal animal) {
        this.animal = animal;

        add(new Img(animal.getAvatar()), Coord.z);

        int offx = LivestockManager.COLUMN_TITLE_X - LivestockManager.ENTRY_X;
        for (Map.Entry<String, Integer> entry : animal.entrySet()) {
            Integer val = entry.getValue();
            if (val == null)
                continue;

            String key = entry.getKey();
            Column col = animal.getColumns().get(key);

            String valStr = val.toString();
            if (key.equals(Resource.getLocString(Resource.BUNDLE_LABEL, "Meat quality:")) ||
                key.equals(Resource.getLocString(Resource.BUNDLE_LABEL, "Milk quality:")) ||
                key.equals(Resource.getLocString(Resource.BUNDLE_LABEL, "Hide quality:")) ||
                key.equals(Resource.getLocString(Resource.BUNDLE_LABEL, "Wool quality:")) ||
                key.equals(Resource.getLocString(Resource.BUNDLE_LABEL, "Endurance:")))
                valStr += "%";

            Label lbl = new Label(valStr, Text.labelFnd);
            add(lbl, new Coord(col.x + offx, 5));
        }

        Label del = new Label("\u2718", Text.delfnd, Color.RED) {
            @Override
            public boolean mousedown(Coord c, int button) {
                delete();
                return true;
            }
        };
        Column col = animal.getColumns().get("X");
        add(del, new Coord(col.x + offx, 3));
    }

    @Override
    public boolean mousedown(Coord c, int button) {
        Gob gob = gameui().map.glob.oc.getgob(animal.gobid);
        if (gob != null) {
            gob.delattr(GobHighlight.class);
            gob.setattr(new GobHighlight(gob));
            if (button == 3)
                gameui().map.wdgmsg("click", gob.sc, gob.rc.floor(posres), 3, 0, 0, (int) gob.id, gob.rc.floor(posres), 0, -1);
        }
        return super.mousedown(c, button);
    }

    @Override
    public void mousemove(Coord c) {
        hover = c.x > 0 && c.x < sz.x && c.y > 0 && c.y < sz.y;
        super.mousemove(c);
    }

    public void delete() {
        reqdestroy();
        int y = this.c.y;
        for (Widget child = parent.lchild; child != null; child = child.prev) {
            if (child instanceof DetailsWdg && child.c.y > y)
                child.c.y -= HEIGHT;
        }

        ((LivestockManager.Panel)parent.parent.parent).delete(animal);

        ((Scrollport.Scrollcont) parent).update();
    }
}
