package commands;

import api.wynn.WynnApi;
import api.wynn.structs.Item;
import api.wynn.structs.ItemDB;
import app.Bot;
import commands.base.GenericCommand;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.exceptions.PermissionException;
import org.jetbrains.annotations.NotNull;
import update.reaction.ReactionManager;
import update.reaction.ReactionResponse;
import utils.ArgumentParser;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static commands.ItemView.*;

public class IdentifyItem extends GenericCommand {
    private final WynnApi wynnApi;
    private final String imageURLBase;
    private final ReactionManager reactionManager;

    public IdentifyItem(Bot bot) {
        this.wynnApi = new WynnApi(bot.getLogger());
        this.imageURLBase = bot.getProperties().githubImagesUrl;
        this.reactionManager = bot.getReactionManager();
    }

    @NotNull
    @Override
    protected String[][] names() {
        return new String[][]{{"id", "identify"}};
    }

    @Override
    public @NotNull String syntax() {
        return "id <item name> [-re]";
    }

    @Override
    public @NotNull String shortHelp() {
        return "Simulates identifying an item.";
    }

    @Override
    public @NotNull Message longHelp() {
        return new MessageBuilder(
                new EmbedBuilder()
                        .setAuthor("Identify Command Help")
                        .setDescription("Simulates identifying an item.")
                        .addField("Re-identifying",
                                "To keep identifying an item multiple times, give an argument `-re` " +
                                        "and press the reaction button on the message.", false)
                        .build()
        ).build();
    }

    @Override
    public long getCoolDown() {
        return TimeUnit.SECONDS.toMillis(1);
    }

    @Override
    public void process(@NotNull MessageReceivedEvent event, @NotNull String[] args) {
        if (args.length < 2) {
            respond(event, "Please input the item name you want to simulate identifying.");
            return;
        }

        ItemDB db = this.wynnApi.mustGetItemDB(false);
        if (db == null) {
            respondError(event, "Something went wrong while requesting Wynncraft API.");
            return;
        }

        ArgumentParser parser = new ArgumentParser(Arrays.copyOfRange(args, 1, args.length));

        String input = parser.getNormalArgument();
        List<Item> matched = searchItem(input, db.getItems());

        if (matched.size() == 0) {
            respond(event, String.format("No items matched with input `%s`.", input));
            return;
        } else if (matched.size() > 1) {
            respond(event, String.format("Multiple items (%s items) matched with input `%s`.\nMatched items: %s",
                    matched.size(), input,
                    matched.stream().limit(50).map(i -> "`" + i.getName() + "`")
                            .collect(Collectors.joining(", "))));
            return;
        }

        Item item = matched.get(0);

        boolean reIdentify = parser.getArgumentMap().containsKey("re");
        if (!reIdentify) {
            respond(event, formatItemInfo(item, this.imageURLBase, 1));
            return;
        }

        respond(event, formatItemInfo(item, this.imageURLBase, 1), message -> {
            IDReactionHandler handler = new IDReactionHandler(message, event.getAuthor().getIdLong(),
                    seq -> formatItemInfo(item, this.imageURLBase, seq));
            this.reactionManager.addEventListener(handler);
        });
    }

    private static class IDReactionHandler extends ReactionResponse {
        private static final String WHITE_CHECK_MARK = "\u2705";
        private static final String X = "\u274C";
        private static final String[] reactions = {WHITE_CHECK_MARK, X};

        private final Message message;
        private final Function<Integer, Message> messageSupplier;
        private int seq;

        private IDReactionHandler(Message message, long userId, Function<Integer, Message> messageSupplier) {
            super(message.getIdLong(), message.getChannel().getIdLong(), userId, false, event -> false);
            this.onReaction = this::onReaction;
            this.setOnDestroy(() -> deletePagingReactions(message.getAuthor()));

            this.message = message;
            this.messageSupplier = messageSupplier;
            this.seq = 1;

            addPagingReactions(message);
        }

        private boolean onReaction(MessageReactionAddEvent event) {
            User author = event.getJDA().getUserById(event.getUserIdLong());
            if (author != null) {
                try {
                    event.getReaction().removeReaction(author).queue();
                } catch (PermissionException ignored) {
                }
            }

            String reactionName = event.getReactionEmote().getName();
            if (reactionName.equals(X)) {
                return true;
            }

            seq++;
            this.message.editMessage(
                    this.messageSupplier.apply(seq)
            ).queue();
            return false;
        }

        /**
         * Adds paging reactions to a message.
         * @param message Message to add reactions to.
         */
        private static void addPagingReactions(Message message) {
            for (String reaction : reactions) {
                message.addReaction(reaction).queue();
            }
        }

        /**
         * Deletes paging reactions from the sent message.
         * @param self Bot user.
         */
        private void deletePagingReactions(User self) {
            for (String reaction : reactions) {
                this.message.removeReaction(reaction, self).queue();
            }
        }
    }

    /**
     * Formats item info and returns formatted message.
     * @param item Item
     * @param imageURLBase Image URl
     * @param seq How many times this item has been identified. Used for display.
     * @return Formatted message.
     */
    private static Message formatItemInfo(Item item, String imageURLBase, int seq) {
        EmbedBuilder eb = getEmbed(item, imageURLBase);
        if (seq > 1) {
            eb.setTitle(String.format("Lv. %s %s [%s], %s %s",
                    item.getLevel(), item.getName(), seq, item.getTier(), item.getType()));
        }
        eb.setDescription("You identified the item!");

        return new MessageBuilder(
                "```ml\n" +
                        getIDs(item) +
                        "\n```"
        ).setEmbeds(
                eb.build()
        ).build();
    }

    private static String getIDs(Item item) {
        List<Identification> availableIDs = Arrays.stream(identifications)
                .filter(i -> i.status.apply(item) != 0).collect(Collectors.toList());

        List<String> ret = new ArrayList<>();
        if (availableIDs.isEmpty()) {
            return "No Identifications";
        }

        int displayJustify = availableIDs.stream().mapToInt(i -> i.displayName.length()).max().getAsInt() + 1;
        for (Identification id : availableIDs) {
            ret.add(String.format("%s%s : %s",
                    nSpaces(displayJustify - id.displayName.length()), id.displayName,
                    getFormattedIdentified(id, item)));
        }

        return String.join("\n", ret);
    }

    private static String getFormattedIdentified(Identification id, Item item) {
        int base = id.status.apply(item);
        if (id.isIdentified() || item.isIdentified()) {
            return base + id.getSuffix();
        }

        int lo, hi, val;
        String stars = "";
        String percentage;
        Random rand = new Random();
        if (base > 0) {
            int roll = rand.nextInt(101);
            val = (int) Math.round((double) base * (roll + 30d) / 100d);
            lo = (int) Math.round((double) base * 0.3d);
            hi = (int) Math.round((double) base * 1.3d);

            if (71 <= roll && roll < 95) stars = " *";
            else if (95 <= roll && roll < 100) stars = " **";
            else if (roll == 100) stars = " ***";

            percentage = String.valueOf(roll);
        } else {
            int roll = rand.nextInt(61);
            val = (int) Math.round((double) base * (roll + 70d) / 100d);
            lo = (int) Math.round((double) base * 1.3d);
            hi = (int) Math.round((double) base * 0.7d);

            percentage = String.valueOf((int) ((double) (60 - roll) * 100d / 60d));
        }

        return String.format("%s%s%s [%s%%], %s%s ~ %s%s",
                val, id.getSuffix(), stars, percentage, lo, id.getSuffix(), hi, id.getSuffix());
    }

    private static String nSpaces(int n) {
        return String.join("", Collections.nCopies(n, " "));
    }
}
