this is pretty much Rust-styled raiding in Minecraft, minus the TC.
------------------------------------------------------

THE BASICS 

All players can claim land using /raid claim <radius>
Inside their claim, blocks placed by the player will have Raid Durability.
All other "natural" blocks are currently invincible from damage by other players, this may need to be changed depending.

Raidable blocks can be repaired or reinforced with the Repair Tool (default: Golden Hoe) 

To repair, right click a damaged block with a replacement block available in your inventory
To reinforce, shift left-click to make a selection of raid blocks to reinforce

Reinforcement Tiers:
T1: Stone Reinforcement - Requires 1x Stone per Block & XP Cost

T2: Iron Reinforcement - Requires 1x Iron Block per Block & XP Cost

T3: Obsidian Reinforcement - Requires 1x Obsidian per Block & XP Cost

These upgrades will be listed out in your chat for you to approve/deny to confirm your choices.

The maximum health added to these blocks from each tier are configurable
You can also create custom maximum health for any block

------------------------------------------------------

RAIDING

Players must be ONLINE to be raided. This is currently vulnerable to PvP Logging, as this plugin was made with the intention for friends, so public "real-world" behaviors were not really considered. 
It's probably not difficult to add a combattimer to allow blocks to continue to be destroyed.

Depending on your configuration, how blocks durability are affected by tools are very specific. 
This also applies to blasts from explosions (like TNT). If you're using a wooden pickaxe to try and break a players base, you shouldn't get very far, regardless of what their house is made out of.
Container blocks cannot be opened by players foreign to a claim, until the container's durability is 0 - They should not break immediately, but CAN be broken like all other raid blocks after the DUR is 0.

Once any raid blocks duration is "0", it will break naturally.

You may view any raid block's durability (assuming it is damaged) by staring directly at the block, the durability should appear on your Action Bar. No action bar means it is not damaged.
Reinforced blocks will always show they are reinforced, regardless of health, on the Action Bar.

------------------------------------------------------

ADMIN

You can use /raid admin safezone/warzone claim/unclaim <radius> to create SafeZone or WarZones. 
This is pretty much classic Factions logic. Players cannot interact with the world inside these claims.
