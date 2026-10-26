package com.mathgulag;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.*;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static net.minecraft.server.command.CommandManager.literal;

public class MathGulag implements ModInitializer {
    public static final String ID = "mathgulag", VERSION = "2.0.0";
    public static EntityType<ClawSkeletonEntity> CLAW_SKELETON;
    static final Map<UUID, Game> GAMES = new HashMap<>();
    static final Random RNG = new Random();
    static int pulse = 0;

    static class Game { int a, b, seed, typed, nd, need, fails, score; UUID label; }

    @Override public void onInitialize() {
        PayloadTypeRegistry.playS2C().register(OpenPayload.ID, OpenPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(AnswerPayload.ID, AnswerPayload.CODEC);
        CLAW_SKELETON = Registry.register(Registries.ENTITY_TYPE, Identifier.of(ID, "claw_skeleton"),
            EntityType.Builder.create(ClawSkeletonEntity::new, SpawnGroup.MONSTER).dimensions(0.8f, 2.6f).build("claw_skeleton"));
        FabricDefaultAttributeRegistry.register(CLAW_SKELETON, ClawSkeletonEntity.attrs());
        Registry.register(Registries.ITEM, Identifier.of(ID, "claw_skeleton_spawn_egg"),
            new SpawnEggItem(CLAW_SKELETON, 0xE8E0C0, 0xAA0000, new Item.Settings()));
        ServerPlayNetworking.registerGlobalReceiver(AnswerPayload.ID, (p, ctx) -> answer(ctx.player(), p.digit()));
        ServerTickEvents.END_SERVER_TICK.register(MathGulag::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((h, s) -> GAMES.remove(h.player.getUuid()));
        CommandRegistrationCallback.EVENT.register((d, reg, env) -> register(d));
    }

    static void register(CommandDispatcher<ServerCommandSource> d) {
        d.register(literal("mathgulag").requires(s -> s.hasPermissionLevel(2))
            .then(literal("start").executes(c -> { start(c.getSource().getPlayerOrThrow()); return 1; }))
            .then(literal("stop").executes(c -> { stop(c.getSource().getPlayerOrThrow()); return 1; }))
            .then(literal("arena").executes(c -> { buildArena(c.getSource().getPlayerOrThrow()); return 1; }))
            .then(literal("heart").executes(c -> { spawnHeart(c.getSource().getPlayerOrThrow(), 0); return 1; }))
            .then(literal("skeleton").executes(c -> { cmd(c.getSource().getPlayerOrThrow(), "summon mathgulag:claw_skeleton ~ ~ ~"); return 1; }))
            .then(literal("version").executes(c -> { c.getSource().sendFeedback(() -> Text.literal("MathGulag v" + VERSION), false); return 1; })));
    }
    static void cmd(ServerPlayerEntity p, String c) {
        p.getServer().getCommandManager().executeWithPrefix(p.getCommandSource().withLevel(4).withSilent(), c);
    }
    static void cmdServer(MinecraftServer s, String c) {
        s.getCommandManager().executeWithPrefix(s.getCommandSource().withLevel(4).withSilent(), c);
    }

    static void start(ServerPlayerEntity p) {
        stop(p);
        Game g = new Game(); GAMES.put(p.getUuid(), g);
        try {
            Entity e = EntityType.TEXT_DISPLAY.create(p.getServerWorld());
            NbtCompound n = StringNbtReader.parse("{billboard:\"center\",Tags:[\"mg_label\"],text:'{\"text\":\"?\"}',transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0.7f,0f],scale:[1f,1f,1f]}}");
            e.readNbt(n); e.setPosition(p.getPos());
            p.getServerWorld().spawnEntity(e); e.startRiding(p, true); g.label = e.getUuid();
        } catch (Exception ex) { ex.printStackTrace(); }
        newQ(p, g);
    }
    static void stop(ServerPlayerEntity p) {
        Game g = GAMES.remove(p.getUuid());
        if (g == null) return;
        if (g.label != null) cmd(p, "kill " + g.label);
        ServerPlayNetworking.send(p, new OpenPayload(-1, 0, 0, "", 0));
    }
    static void newQ(ServerPlayerEntity p, Game g) {
        g.a = 2 + RNG.nextInt(11); g.b = 2 + RNG.nextInt(11); g.typed = 0; g.nd = 0; g.seed = RNG.nextInt();
        int ans = g.a * g.b; g.need = ans >= 100 ? 3 : ans >= 10 ? 2 : 1;
        send(p, g, 0);
    }
    static void send(ServerPlayerEntity p, Game g, int flash) {
        String t = g.nd == 0 ? "?" : String.valueOf(g.typed);
        ServerPlayNetworking.send(p, new OpenPayload(g.a, g.b, g.seed, t, flash));
        if (g.label != null) cmd(p, "data merge entity " + g.label + " {text:'{\"text\":\"" + g.a + " x " + g.b + " = " + t + "\"}'}");
    }
    static void answer(ServerPlayerEntity p, int d) {
        Game g = GAMES.get(p.getUuid()); if (g == null || d < 0 || d > 9) return;
        g.typed = g.typed * 10 + d; g.nd++;
        if (g.nd < g.need) { send(p, g, 0); return; }
        if (g.typed == g.a * g.b) {
            g.score++; p.sendMessage(Text.literal("§aCORRECTO §7(" + g.score + ")"), true); newQ(p, g);
        } else {
            g.fails++; g.typed = 0; g.nd = 0;
            if (g.fails >= 3) {
                p.getServer().getPlayerManager().broadcast(Text.literal("§c" + p.getName().getString() + " perdió"), false);
                stop(p);
            } else send(p, g, 1);
        }
    }

    static void spawnHeart(ServerPlayerEntity p, int dy) {
        cmd(p, "summon minecraft:item_display ~ ~" + dy + " ~ {Tags:[\"mg_heart\"],item:{id:\"minecraft:stick\",count:1,components:{\"minecraft:custom_model_data\":1}},item_display:\"none\",brightness:{sky:15,block:15},view_range:4f,transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[12f,12f,12f]}}");
    }
    static void buildArena(ServerPlayerEntity p) {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(MathGulag.class.getResourceAsStream("/data/mathgulag/arena.txt"), StandardCharsets.UTF_8))) {
            String line; while ((line = r.readLine()) != null) if (!line.isBlank()) cmd(p, line);
        } catch (Exception e) { e.printStackTrace(); return; }
        spawnHeart(p, 8);
        cmd(p, "tp @s ~ ~-8 ~");
    }
    static String beat(double s) { return "execute as @e[type=minecraft:item_display,tag=mg_heart] run data merge entity @s {start_interpolation:0,interpolation_duration:" + (s > 12 ? 3 : 6) + ",transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[" + s + "f," + s + "f," + s + "f]}}"; }
    static void tick(MinecraftServer s) {
        pulse = (pulse + 1) % 40;
        switch (pulse) {
            case 0 -> { cmdServer(s, beat(13.6)); cmdServer(s, "execute at @e[type=minecraft:item_display,tag=mg_heart] run playsound minecraft:entity.warden.heartbeat master @a ~ ~ ~ 6 0.6"); }
            case 4, 14 -> cmdServer(s, beat(12.0));
            case 10 -> { cmdServer(s, beat(12.9)); cmdServer(s, "execute at @e[type=minecraft:item_display,tag=mg_heart] run playsound minecraft:entity.warden.heartbeat master @a ~ ~ ~ 5 0.5"); }
            default -> {}
        }
    }
}
