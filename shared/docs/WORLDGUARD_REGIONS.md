# WorldGuard Regions

/rg define spawn
/rg flag spawn block-break deny block-place deny pvp deny tnt deny creeper-explosion deny other-explosion deny fire-spread deny lava-fire deny lighter deny item-drop deny item-pickup deny damage-animals deny mob-spawning deny
/rg define lobby
/rg flag lobby block-break deny block-place deny pvp deny tnt deny creeper-explosion deny other-explosion deny fire-spread deny lava-fire deny lighter deny item-drop deny item-pickup deny damage-animals deny mob-spawning deny
/rg define practice-safe
/rg flag practice-safe pvp deny block-break deny block-place deny
/rg define practice-pvp
/rg flag practice-pvp pvp allow block-break deny block-place deny

MaceCupCore adds backup listener protections for lobby items, GUI items, protected interactions, and event safety.
