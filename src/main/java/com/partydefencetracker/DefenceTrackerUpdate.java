package com.partydefencetracker;

import lombok.EqualsAndHashCode;
import lombok.Value;
import net.runelite.client.party.messages.PartyMemberMessage;

@Value
@EqualsAndHashCode(callSuper = true)
public class DefenceTrackerUpdate extends PartyMemberMessage
{
    public DefenceTrackerUpdate(String bossName, String specWeapon, int hit, boolean alive, int world)
    {
        this.bossName = bossName;
        this.specWeapon = specWeapon;
        this.hit = hit;
        this.alive = alive;
        this.world = world;
    }

    String bossName;
    String specWeapon;
    int hit;
    boolean alive;
    int world;
}
