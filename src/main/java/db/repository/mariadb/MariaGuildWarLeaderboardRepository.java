package db.repository.mariadb;

import db.ConnectionPool;
import db.model.guildWarLeaderboard.GuildWarLeaderboard;
import db.model.guildWarLeaderboard.GuildWarLeaderboardId;
import db.repository.base.GuildWarLeaderboardRepository;
import log.Logger;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class MariaGuildWarLeaderboardRepository extends GuildWarLeaderboardRepository {
    MariaGuildWarLeaderboardRepository(ConnectionPool db, Logger logger) {
        super(db, logger);
    }

    @Override
    protected GuildWarLeaderboard bind(@NotNull ResultSet res) throws SQLException {
        return new GuildWarLeaderboard(
                res.getString(1), res.getInt(2), res.getInt(3),
                res.getBigDecimal(4).doubleValue()
        );
    }

    @Override
    public <S extends GuildWarLeaderboard> boolean create(@NotNull S entity) {
        return this.execute(
                "INSERT INTO `guild_war_leaderboard` (guild_name, total_war, success_war) VALUES (?, ?, ?)",
                entity.getGuildName(),
                entity.getTotalWar(),
                entity.getSuccessWar()
        );
    }

    @Override
    public boolean exists(@NotNull GuildWarLeaderboardId guildWarLeaderboardId) {
        ResultSet res = this.executeQuery(
                "SELECT COUNT(*) FROM `guild_war_leaderboard` WHERE `guild_name` = ?",
                guildWarLeaderboardId.getGuildName()
        );

        if (res == null) {
            return false;
        }

        try {
            if (res.next())
                return res.getInt(1) > 0;
        } catch (SQLException e) {
            this.logResponseException(e);
        }
        return false;
    }

    @Override
    public long count() {
        ResultSet res = this.executeQuery(
                "SELECT COUNT(*) FROM `guild_war_leaderboard`"
        );

        if (res == null) {
            return -1;
        }

        try {
            if (res.next())
                return res.getInt(1);
        } catch (SQLException e) {
            this.logResponseException(e);
        }
        return -1;
    }

    @Nullable
    @Override
    public GuildWarLeaderboard findOne(@NotNull GuildWarLeaderboardId guildWarLeaderboardId) {
        ResultSet res = this.executeQuery(
                "SELECT * FROM `guild_war_leaderboard` WHERE `guild_name` = ?",
                guildWarLeaderboardId.getGuildName()
        );

        if (res == null) {
            return null;
        }

        try {
            if (res.next())
                return bind(res);
        } catch (SQLException e) {
            this.logResponseException(e);
        }
        return null;
    }

    @Nullable
    @Override
    public List<GuildWarLeaderboard> findAll() {
        ResultSet res = this.executeQuery(
                "SELECT * FROM `guild_war_leaderboard`"
        );

        if (res == null) {
            return null;
        }

        try {
            return bindAll(res);
        } catch (SQLException e) {
            this.logResponseException(e);
            return null;
        }
    }

    @Nullable
    @Override
    public List<GuildWarLeaderboard> getByTotalWarDescending(int limit, int offset) {
        ResultSet res = this.executeQuery(
                "SELECT * FROM `guild_war_leaderboard` ORDER BY `total_war` DESC, `guild_name` DESC LIMIT " + limit + " OFFSET " + offset
        );

        if (res == null) {
            return null;
        }

        try {
            return bindAll(res);
        } catch (SQLException e) {
            this.logResponseException(e);
            return null;
        }
    }

    @Nullable
    @Override
    public List<GuildWarLeaderboard> getBySuccessWarDescending(int limit, int offset) {
        ResultSet res = this.executeQuery(
                "SELECT * FROM `guild_war_leaderboard` ORDER BY `success_war` DESC, `guild_name` DESC LIMIT " + limit + " OFFSET " + offset
        );

        if (res == null) {
            return null;
        }

        try {
            return bindAll(res);
        } catch (SQLException e) {
            this.logResponseException(e);
            return null;
        }
    }

    @Override
    public boolean update(@NotNull GuildWarLeaderboard entity) {
        return this.execute(
                "UPDATE `guild_war_leaderboard` SET `total_war` = ?, `success_war` = ? WHERE `guild_name` = ?",
                entity.getTotalWar(),
                entity.getSuccessWar(),
                entity.getGuildName()
        );
    }

    @Override
    public boolean delete(@NotNull GuildWarLeaderboardId guildWarLeaderboardId) {
        return this.execute(
                "DELETE FROM `guild_war_leaderboard` WHERE `guild_name` = ?",
                guildWarLeaderboardId.getGuildName()
        );
    }
}
