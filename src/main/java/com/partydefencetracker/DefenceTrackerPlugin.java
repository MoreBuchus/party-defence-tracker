/*
 * Copyright (c) 2022, Buchus <http://github.com/MoreBuchus>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.partydefencetracker;

import com.google.inject.Provides;
import java.awt.*;
import java.util.*;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.kit.KitType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;
import net.runelite.client.util.ColorUtil;

@PluginDescriptor(
		name = "Party Defence Tracker",
		description = "Calculates the defence based off party specs",
		tags = {"party", "defence", "tracker", "boosting", "special"}
)
@Slf4j
public class DefenceTrackerPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private PartyService partyService;

	@Inject
	private WSClient wsClient;

	@Inject
	private DefenceTrackerConfig config;

	@Inject
	private SkillIconManager skillIconManager;

	@Inject
	private InfoBoxManager infoBoxManager;

	public String boss = "";
	public String specWep = "";
	public double bossDef = -1;
	public DefenceInfoBox box = null;
	private VulnerabilityInfoBox vulnBox = null;
	public SpritePixels vuln = null;
	public boolean vulnHit;
	public boolean isInCm = false;
	public ArrayList<String> bossList = new ArrayList<>(Arrays.asList(
			"Abyssal Sire", "Callisto", "Cerberus", "Chaos Elemental", "Corporeal Beast", "General Graardor", "Giant Mole",
			"Kalphite Queen", "King Black Dragon", "K'ril Tsutsaroth", "Sarachnis", "Venenatis", "Vet'ion", "Vet'ion Reborn",
			"The Maiden of Sugadinti", "Pestilent Bloat", "Nylocas Vasilias", "Sotetseg", "Xarpus",
			"Great Olm (Left claw)", "Tekton", "Tekton (enraged)"));
	public boolean hmXarpus = false;

	private static final int MAIDEN_REGION = 12613;
	private static final int BLOAT_REGION = 13125;
	private static final int NYLOCAS_REGION = 13122;
	private static final int SOTETSEG_REGION = 13123;
	private static final int SOTETSEG_MAZE_REGION = 13379;
	private static final int XARPUS_REGION = 12612;
	private static final int VERZIK_REGION = 12611;
	boolean bloatDown = false;

	private int ATTACK;
	private int STRENGTH;
	private int DEFENCE;
	private int RANGED;
	private int MAGIC;

	@Provides
	DefenceTrackerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(DefenceTrackerConfig.class);
	}

	protected void startUp() throws Exception
	{
		reset();
		wsClient.registerMessage(DefenceTrackerUpdate.class);
	}

	protected void shutDown() throws Exception
	{
		reset();
		wsClient.unregisterMessage(DefenceTrackerUpdate.class);
	}

	protected void reset()
	{
		infoBoxManager.removeInfoBox(box);
		infoBoxManager.removeInfoBox(vulnBox);
		boss = "";
		bossDef = -1;
		specWep = "";
		box = null;
		vulnBox = null;
		vuln = null;
		vulnHit = false;
		isInCm = config.cm();
		bloatDown = false;
		ATTACK = -1;
		STRENGTH = -1;
		DEFENCE = -1;
		RANGED = -1;
		MAGIC = -1;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged e)
	{
		isInCm = config.cm();
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged e)
	{
		if (e.getActor() != null && client.getLocalPlayer() != null && e.getActor().getName() != null)
		{
			int animation = e.getActor().getAnimation();
			if (e.getActor().getName().equals(client.getLocalPlayer().getName()))
			{
				if (animation == 1378)
				{
					specWep = "dwh";
				}
				else if (animation == 7642 || animation == 7643)
				{
					specWep = "bgs";
				}
				else if(animation == 2890)
				{
					specWep = "arclight";
				}
				else
				{
					specWep = "";
				}

				if (animation == 1816 && boss.equalsIgnoreCase("sotetseg") && (isInOverWorld() || isInUnderWorld()))
				{
					infoBoxManager.removeInfoBox(box);
					bossDef = 250;
				}
			}
		}

		if (e.getActor() instanceof NPC && e.getActor().getName() != null && e.getActor().getName().equalsIgnoreCase("pestilent bloat"))
		{
			bloatDown = e.getActor().getAnimation() == 8082;
		}
	}

	@Subscribe
	public void onGameTick(GameTick e)
	{
		if (client.getLocalPlayer() != null && MAGIC == -1)
		{
			initStatXp();
		}

		for (NPC n : client.getNpcs())
		{
			if (n != null && n.getName() != null && n.getName().equals(boss) && (n.isDead() || n.getHealthRatio() == 0))
			{
				UpdateBoss(boss, "", 0, false);
			}
		}
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied e)
	{
		if (!(e.getActor() instanceof NPC))
		{
			return;
		}

		NPC npc = (NPC) e.getActor();

		if (npc.getName().contains("Maiden") || npc.getName().contains("Sotetseg") || npc.getName().contains("Xarpus") || npc.getName().contains("Nylocas Vasilias"))
		{
			return;
		}

		if (!specWep.equals("") && e.getHitsplat().isMine() && e.getActor() instanceof NPC && e.getActor() != null
				&& e.getActor().getName() != null && bossList.contains(e.getActor().getName()))
		{
			String bossName;
			if (e.getActor().getName().contains("Tekton"))
			{
				bossName = "Tekton";
			}
			else
			{
				bossName = e.getActor().getName();
			}

			UpdateBoss(bossName, specWep, e.getHitsplat().getAmount(), true);
			specWep = "";
		}
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned e)
	{
		NPC npc = e.getNpc();
		hmXarpus = npc.getId() >= 10770 && npc.getId() <= 10772;
	}

	@Subscribe
	public void onActorDeath(ActorDeath e)
	{
		if (e.getActor() instanceof NPC && e.getActor().getName() != null && client.getLocalPlayer() != null)
		{
			if (e.getActor().getName().equals(boss) || (e.getActor().getName().contains("Tekton") && boss.equals("Tekton")))
			{
				UpdateBoss(boss, "", 0, false);
			}
		}
	}

	public void UpdateBoss(String bossName, String weapon, int hit, boolean alive)
	{
		partyService.send(new DefenceTrackerUpdate(bossName, weapon, hit, alive, client.getWorld()));
	}

	@Subscribe
	public void onDefenceTrackerUpdate(DefenceTrackerUpdate e)
	{
		String weapon = e.getSpecWeapon();
		int hit = e.getHit();
		int world = e.getWorld();

		clientThread.invoke(() ->
		{
			if (!e.isAlive())
			{
				reset();
			}
			else
			{
				if (!boss.equals(e.getBossName()))
				{
					bossDef = -1;
					boss = e.getBossName();
				}

				if (world != client.getWorld())
				{
					return;
				}

				if (((boss.equals("Tekton") || boss.contains("Great Olm")) && client.getVarbitValue(Varbits.IN_RAID) != 1) ||
						((boss.contains("The Maiden of Sugadinti") || boss.contains("Pestilent Bloat") || boss.contains("Nylocas Vasilias")
								|| boss.contains("Sotetseg") || boss.contains("Xarpus")) && client.getVarbitValue(Varbits.THEATRE_OF_BLOOD) != 2))
				{
					return;
				}

				if (bossDef == -1)
				{
					switch (boss)
					{
						case "Abyssal Sire":
						case "Sotetseg":
						case "General Graardor":
							bossDef = 250;
							break;
						case "Callisto":
							bossDef = 440;
							break;
						case "Cerberus":
							bossDef = 110;
							break;
						case "Chaos Elemental":
						case "K'ril Tsutsaroth":
							bossDef = 270;
							break;
						case "Corporeal Beast":
							bossDef = 310;
							break;
						case "Giant Mole":
						case "The Maiden of Sugadinti":
							bossDef = 200;
							break;
						case "Kalphite Queen":
							bossDef = 300;
							break;
						case "King Black Dragon":
							bossDef = 240;
							break;
						case "Sarachnis":
							bossDef = 150;
							break;
						case "Venenatis":
							bossDef = 490;
							break;
						case "Vet'ion":
						case "Vet'ion Reborn":
							bossDef = 395;
							break;
						case "Pestilent Bloat":
							bossDef = 100;
							break;
						case "Nylocas Vasilias":
							bossDef = 50;
							break;
						case "Xarpus":
							if (hmXarpus)
							{
								bossDef = 200;
							}
							else
							{
								bossDef = 250;
							}
							break;
						case "Great Olm (Left claw)":
							bossDef = 175 * (1 + (.01 * (client.getVarbitValue(5424) - 1)));

							if (isInCm)
							{
								bossDef = bossDef * 1.5;
							}
							break;
						case "Tekton":
							bossDef = 205 * (1 + (.01 * (client.getVarbitValue(5424) - 1)));

							if (isInCm)
							{
								bossDef = bossDef * 1.2;
							}
							break;
					}
				}

				if (weapon.equals("dwh"))
				{
					if (hit == 0)
					{
						if (client.getVarbitValue(Varbits.IN_RAID) == 1 && boss.equals("Tekton"))
						{
							bossDef -= bossDef * .05;
						}
					}
					else
					{
						bossDef -= bossDef * .30;
					}
				}
				else if (weapon.equals("bgs"))
				{
					if (hit == 0)
					{
						if (client.getVarbitValue(Varbits.IN_RAID) == 1 && boss.equals("Tekton"))
						{
							bossDef -= 10;
						}
					}
					else
					{
						if (boss.equals("Corporeal Beast") || (isInBloat() && boss.equals("Pestilent Bloat") && !bloatDown))
						{
							bossDef -= hit * 2;
						}
						else
						{
							bossDef -= hit;
						}
					}
				}
				else if (weapon.equals("arclight") && hit > 0)
				{
					if (boss.equals("K'ril Tsutsaroth"))
					{
						bossDef -= bossDef * .10;
					}
					else
					{
						bossDef -= bossDef * .05;
					}
				}
				else if (weapon.equals("vuln"))
				{
					if (config.vulnerability())
					{
						infoBoxManager.removeInfoBox(vulnBox);
						IndexDataBase sprite = client.getIndexSprites();
						vuln = Objects.requireNonNull(client.getSprites(sprite, 56, 0))[0];
						vulnBox = new VulnerabilityInfoBox(vuln.toBufferedImage(), this);
						vulnBox.setTooltip(ColorUtil.wrapWithColorTag(boss, Color.WHITE));
						infoBoxManager.addInfoBox(vulnBox);
					}
					vulnHit = true;
					bossDef -= bossDef * .1;
				}

				if (bossDef < 0)
				{
					bossDef = 0;
				}
				infoBoxManager.removeInfoBox(box);
				box = new DefenceInfoBox(skillIconManager.getSkillImage(Skill.DEFENCE), this, Math.round(bossDef), config);
				box.setTooltip(ColorUtil.wrapWithColorTag(boss, Color.WHITE));
				infoBoxManager.addInfoBox(box);
			}
		});
	}

	@Subscribe
	private void onVarbitChanged(VarbitChanged e)
	{
		if ((client.getVarbitValue(Varbits.IN_RAID) != 1 && (boss.equals("Tekton") || boss.equals("Great Olm (Left claw)")))
				|| (boss.equals("The Maiden of Sugadinti") && !isInMaiden()) || (boss.equals("Pestilent Bloat") && !isInBloat())
				|| (boss.equals("Nylocas Vasilias") && !isInNylo()) || (boss.equals("Sotetseg") && !isInOverWorld() && !isInUnderWorld())
				|| (boss.equals("Xarpus") && !isInXarpus()))
		{
			reset();
		}
	}

	@Subscribe
	public void onGraphicChanged(GraphicChanged e)
	{
		//85 = splash
		if (e.getActor().getName() != null && e.getActor().getGraphic() == 169)
		{
			if (bossList.contains(e.getActor().getName()))
			{
				if (e.getActor().getName().contains("Tekton"))
				{
					boss = "Tekton";
				}
				else
				{
					boss = e.getActor().getName();
				}

				UpdateBoss(boss, "vuln", 0, true);
			}
		}
	}

	@Subscribe
	public void onFakeXpDrop(FakeXpDrop event) throws InterruptedException
	{
		int xpdiff = event.getXp();
		String skill = event.getSkill().toString();
		if (!skill.equals("RANGED") && !skill.equals("MAGIC") && !skill.equals("STRENGTH") && !skill.equals("ATTACK") && !skill.equals("DEFENCE"))
		{
			return;
		}

		switch (skill)
		{
			case "MAGIC":
				xpdiff = event.getXp() - MAGIC;
				MAGIC = event.getXp();
				break;
			case "RANGED":
				xpdiff = event.getXp() - RANGED;
				RANGED = event.getXp();
				break;
			case "STRENGTH":
				xpdiff = event.getXp() - STRENGTH;
				STRENGTH = event.getXp();
				break;
			case "ATTACK":
				xpdiff = event.getXp() - ATTACK;
				ATTACK = event.getXp();
				break;
			case "DEFENCE":
				xpdiff = event.getXp() - DEFENCE;
				DEFENCE = event.getXp();
				break;
		}

		processXpDrop(String.valueOf(xpdiff), skill);
	}

	@Subscribe
	public void onStatChanged(StatChanged event) throws InterruptedException
	{
		int xpdiff = 0;
		String skill = event.getSkill().toString();
		if (!skill.equals("RANGED") && !skill.equals("MAGIC") && !skill.equals("STRENGTH") && !skill.equals("ATTACK") && !skill.equals("DEFENCE"))
		{
			return;
		}

		switch (skill)
		{
			case "MAGIC":
				xpdiff = event.getXp() - MAGIC;
				MAGIC = event.getXp();
				break;
			case "RANGED":
				xpdiff = event.getXp() - RANGED;
				RANGED = event.getXp();
				break;
			case "STRENGTH":
				xpdiff = event.getXp() - STRENGTH;
				STRENGTH = event.getXp();
				break;
			case "ATTACK":
				xpdiff = event.getXp() - ATTACK;
				ATTACK = event.getXp();
				break;
			case "DEFENCE":
				xpdiff = event.getXp() - DEFENCE;
				DEFENCE = event.getXp();
				break;
		}

		processXpDrop(String.valueOf(xpdiff), skill);
	}

	private void processXpDrop(String xpDrop, String skill) throws InterruptedException
	{
		int damage = 0;
		int weaponUsed = Objects.requireNonNull(Objects.requireNonNull(client.getLocalPlayer()).getPlayerComposition()).getEquipmentId(KitType.WEAPON);
		boolean isDWH = (weaponUsed == ItemID.DRAGON_WARHAMMER);
		boolean isBGS = (weaponUsed == ItemID.BANDOS_GODSWORD || weaponUsed == ItemID.BANDOS_GODSWORD_OR);
		boolean isSpecWeapon = isDWH || isBGS;
		Actor interacted = client.getLocalPlayer().getInteracting();
		String bossName = "";
		if (interacted instanceof NPC)
		{
			bossName = interacted.getName();
		}

		if (bossName != null && !(bossName.contains("Maiden") || bossName.contains("Sotetseg") || bossName.contains("Xarpus") || bossName.contains("Nylocas Vasilias")))
		{
			return;
		}

		if (bossList.contains(bossName))
		{
			switch (skill)
			{
				case "ATTACK":
				case "STRENGTH":
				case "DEFENCE":
					if (isSpecWeapon)
					{
						damage = (int) (Integer.parseInt(xpDrop) / 4.0);
					}
					break;
			}

			if (isDWH)
			{
				specWep = "dwh";
			}
			else if (isBGS)
			{
				specWep = "bgs";
			}

			UpdateBoss(bossName, specWep, damage, true);
		}
	}

	private boolean isInMaiden()
	{
		return client.getMapRegions().length > 0 && client.getMapRegions()[0] == MAIDEN_REGION;
	}

	private boolean isInBloat()
	{
		return client.getMapRegions().length > 0 && client.getMapRegions()[0] == BLOAT_REGION;
	}

	private boolean isInNylo()
	{
		return client.getMapRegions().length > 0 && client.getMapRegions()[0] == NYLOCAS_REGION;
	}

	private boolean isInOverWorld()
	{
		return client.getMapRegions().length > 0 && client.getMapRegions()[0] == SOTETSEG_REGION;
	}

	private boolean isInUnderWorld()
	{
		return client.getMapRegions().length > 0 && client.getMapRegions()[0] == SOTETSEG_MAZE_REGION;
	}

	private boolean isInXarpus()
	{
		return client.getMapRegions().length > 0 && client.getMapRegions()[0] == XARPUS_REGION;
	}

	private boolean isInVerzik()
	{
		return client.getMapRegions().length > 0 && client.getMapRegions()[0] == VERZIK_REGION;
	}

	private void initStatXp()
	{
		ATTACK = client.getSkillExperience(Skill.ATTACK);
		STRENGTH = client.getSkillExperience(Skill.STRENGTH);
		DEFENCE = client.getSkillExperience(Skill.DEFENCE);
		RANGED = client.getSkillExperience(Skill.RANGED);
		MAGIC = client.getSkillExperience(Skill.MAGIC);
	}
}
