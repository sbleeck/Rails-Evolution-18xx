package rails.game.specific._1835;

import java.awt.Color;
import net.sf.rails.game.PublicCompany;
import net.sf.rails.game.MapHex;
import net.sf.rails.game.Stop;
import net.sf.rails.game.Station;
import net.sf.rails.game.state.Owner;
import rails.game.action.GuiTargetedAction;
import rails.game.action.LayBaseToken;

public class LayBadenHomeToken extends LayBaseToken implements GuiTargetedAction {

    private static final long serialVersionUID = 1L;
    private final String label;

    public LayBadenHomeToken(MapHex hex, PublicCompany company, Stop stop) {
        super(hex.getRoot(), hex);
        // Cast or direct use of PublicCompany to satisfy LayBaseToken.setCompany()
        setCompany(company);
        setChosenStation(stop.getRelatedStationNumber());
        setType(LayBaseToken.HOME_CITY);
        
        Station station = hex.getStation(stop.getRelatedStationNumber());
        this.label = String.format("Place Baden Home Station (%s)", 
                                   hex.getConnectionString(station));
    }

    @Override
    public Owner getActor() {
        return getCompany();
    }

    @Override
    public String getGroupLabel() {
        return "Baden Setup";
    }

    @Override
    public String getButtonLabel() {
        return label;
    }

    @Override
    public Color getButtonColor() {
        return new Color(255, 140, 0); 
    }
}