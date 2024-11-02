package com.selfdot.cobblemonmegas.common;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleStartedPreEvent;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.pokemon.feature.FlagSpeciesFeatureProvider;
import com.cobblemon.mod.common.api.pokemon.feature.SpeciesFeatures;
import com.cobblemon.mod.common.api.pokemon.helditem.HeldItemProvider;
import com.cobblemon.mod.common.pokemon.Species;
import com.selfdot.cobblemonmegas.common.command.CommandTree;
import com.selfdot.cobblemonmegas.common.command.permissions.PermissionValidator;
import com.selfdot.cobblemonmegas.common.command.permissions.VanillaPermissionValidator;
import com.selfdot.cobblemonmegas.common.item.MegaStoneHeldItemManager;
import com.selfdot.cobblemonmegas.common.util.DisableableMod;
import com.selfdot.cobblemonmegas.common.util.MegaUtils;
import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.PlayerEvent;
import kotlin.Unit;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
public class CobblemonMegas extends DisableableMod {

    private static final CobblemonMegas INSTANCE = new CobblemonMegas();
    private CobblemonMegas() { }
    public static CobblemonMegas getInstance() {
        return INSTANCE;
    }

    @Getter
    private MinecraftServer server;
    @Getter
    @Setter
    private PermissionValidator permissionValidator = new VanillaPermissionValidator();
    private final Set<UUID> TO_MEGA_EVOLVE_THIS_TURN = new HashSet<>();
    private final Set<UUID> HAS_MEGA_EVOLVED_THIS_BATTLE = new HashSet<>();

    @Getter
    private Config config;

    public Set<UUID> getToMegaEvolveThisTurn() {
        return TO_MEGA_EVOLVE_THIS_TURN;
    }

    public Set<UUID> getHasMegaEvolvedThisBattle() {
        return HAS_MEGA_EVOLVED_THIS_BATTLE;
    }

    public void onInitialize() {
        config = new Config(this);

        CommandRegistrationEvent.EVENT.register(CommandTree::register);
        LifecycleEvent.SERVER_STARTING.register(this::onServerStarting);
        CobblemonEvents.BATTLE_STARTED_PRE.subscribe(Priority.NORMAL, this::onBattleStartedPre);
        PlayerEvent.PICKUP_ITEM_PRE.register(this::onItemPickup);
        PlayerEvent.DROP_ITEM.register(this::onItemDrop);
        PlayerEvent.PLAYER_JOIN.register(this::onPlayerJoin);
    }

    private static void registerAspectProvider(String aspect) {
        SpeciesFeatures.INSTANCE.register(aspect, new FlagSpeciesFeatureProvider(List.of(aspect), false));
    }

    private void onServerStarting(MinecraftServer server) {
        this.server = server;
        registerAspectProvider(DataKeys.MEGA);
        registerAspectProvider(DataKeys.MEGA_X);
        registerAspectProvider(DataKeys.MEGA_Y);
        registerAspectProvider(DataKeys.PRIMAL);
        for (Species species : PokemonSpecies.INSTANCE.getSpecies()) {
            if (species.getForms().stream().anyMatch(form -> form.getName().equalsIgnoreCase(DataKeys.MEGA))) {
                species.getFeatures().add(DataKeys.MEGA);
            }
            if (species.getForms().stream().anyMatch(form -> form.getName().equalsIgnoreCase(DataKeys.PRIMAL))) {
                species.getFeatures().add(DataKeys.PRIMAL);
            }
            if (species.getForms().stream().anyMatch(form -> form.getName().equalsIgnoreCase(DataKeys.MEGA_X))) {
                species.getFeatures().add(DataKeys.MEGA_X);
                species.getFeatures().add(DataKeys.MEGA_Y);
            }
        }
        MegaStoneHeldItemManager.getInstance().loadMegaStoneIds();
        HeldItemProvider.INSTANCE.register(MegaStoneHeldItemManager.getInstance(), Priority.HIGH);
        config.reload();
    }

    private static final Identifier KEY_STONE = new Identifier("cobblemon", "key_stone");
    private Unit onBattleStartedPre(BattleStartedPreEvent event) {
        MegaUtils.deMegaEvolveAllPlayers(event.getBattle());
        event.getBattle().getPlayers().forEach(
            player -> {
                Set<Identifier> keyItems = Cobblemon.playerData.get(player).getKeyItems();
                if (player.getInventory().containsAny(
                    itemStack -> !itemStack.isEmpty() && itemStack.getOrCreateNbt().contains(DataKeys.NBT_KEY_KEY_STONE)
                )) {
                    keyItems.add(KEY_STONE);
                } else {
                    keyItems.remove(KEY_STONE);
                }
            }
        );
        return Unit.INSTANCE;
    }

    private EventResult onItemPickup(PlayerEntity player, ItemEntity itemEntity, ItemStack itemStack) {
        MegaUtils.updateKeyStoneGlow(itemStack, player);
        return EventResult.pass();
    }

    private EventResult onItemDrop(PlayerEntity player, ItemEntity itemEntity) {
        NbtCompound nbt = itemEntity.getStack().getNbt();
        if (nbt == null || !nbt.getBoolean(DataKeys.NBT_KEY_KEY_STONE)) return EventResult.pass();
        nbt.remove("Enchantments");
        return EventResult.pass();
    }

    private void onPlayerJoin(ServerPlayerEntity player) {
        MegaUtils.updateKeyStoneGlow(player);
    }

}
