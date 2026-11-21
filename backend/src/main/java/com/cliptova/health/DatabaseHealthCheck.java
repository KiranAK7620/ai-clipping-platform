package com.cliptova.health;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import org.springframework.stereotype.Component;

@Component
public class DatabaseHealthCheck {
    private final DataSource dataSource;

    public DatabaseHealthCheck(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public boolean isAvailable() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(2);
        } catch (SQLException exception) {
            return false;
        }
    }
}
