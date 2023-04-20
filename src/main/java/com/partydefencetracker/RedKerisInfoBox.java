package com.partydefencetracker;

import lombok.Getter;
import lombok.Setter;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.overlay.infobox.InfoBox;

import javax.inject.Inject;
import java.awt.*;
import java.awt.image.BufferedImage;

public class RedKerisInfoBox extends InfoBox
{
    private DefenceTrackerPlugin plugin;

    @Inject
    private final DefenceTrackerConfig config;

    @Getter
    @Setter
    private long timer;

    RedKerisInfoBox(BufferedImage image, Plugin plugin, long timer, DefenceTrackerConfig config)
    {
        super(image, plugin);
        this.timer = timer;
        this.config = config;
    }

    public String getText()
    {
        return Long.toString(getTimer());
    }

    public Color getTextColor()
    {
        return Color.WHITE;
    }
}
