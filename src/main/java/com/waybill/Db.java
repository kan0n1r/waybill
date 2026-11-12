package com.waybill;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;

public final class Db {
    private static HikariDataSource ds;

    public static synchronized DataSource get() {
        if (ds == null) {
            HikariConfig c = new HikariConfig();
            c.setDriverClassName("org.postgresql.Driver");
            c.setJdbcUrl(Config.env("DB_URL", "jdbc:postgresql://localhost:5432/waybill"));
            c.setUsername(Config.env("DB_USER", "waybill"));
            c.setPassword(Config.env("DB_PASSWORD", "waybill"));
            c.setMaximumPoolSize(8);
            c.setInitializationFailTimeout(60_000);
            ds = new HikariDataSource(c);
        }
        return ds;
    }

    public static Connection conn() throws SQLException {
        return get().getConnection();
    }

    public static List<Map<String, Object>> query(String sql, Object... params) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                return rows(rs);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public static Map<String, Object> one(String sql, Object... params) {
        List<Map<String, Object>> rows = query(sql, params);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public static int update(String sql, Object... params) {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, params);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void bind(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
    }

    public static List<Map<String, Object>> rows(ResultSet rs) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        List<Map<String, Object>> out = new ArrayList<>();
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= md.getColumnCount(); i++) row.put(md.getColumnLabel(i).toLowerCase(), rs.getObject(i));
            out.add(row);
        }
        return out;
    }

    private Db() {}
}
