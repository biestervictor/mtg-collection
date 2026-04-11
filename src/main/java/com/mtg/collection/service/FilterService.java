package com.mtg.collection.service;

import com.mtg.collection.dto.UserFilter;
import com.mtg.collection.model.CardState;
import com.mtg.collection.model.Printing;
import com.mtg.collection.model.Rarity;
import org.springframework.stereotype.Service;

@Service
public class FilterService {

    public String getDefaultState() {
        return CardState.ALL.getValue();
    }

    public String[] getPrintingOptions() {
        return new String[]{Printing.REGULAR.getValue(), Printing.FOIL.getValue()};
    }

    public String[] getRarityOptions() {
        return new String[]{Rarity.MYTHIC.getValue(), Rarity.RARE.getValue(), 
                           Rarity.UNCOMMON.getValue(), Rarity.COMMON.getValue()};
    }

    public String[] getStateOptions() {
        return new String[]{CardState.ALL.getValue(), CardState.OWNED.getValue(), 
                           CardState.MISSING.getValue(), CardState.TRADABLE.getValue()};
    }
}
