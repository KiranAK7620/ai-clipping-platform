package com.cliptova.health;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DatabaseHealthCheckTest {
    private final DataSource dataSource = mock(DataSource.class);
    private final Connection connection = mock(Connection.class);
    private final DatabaseHealthCheck check = new DatabaseHealthCheck(dataSource);

    @BeforeEach
    void connect() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
    }

    @Test
    void returnsUpAndReleasesAValidConnection() throws SQLException {
        when(connection.isValid(2)).thenReturn(true);

        assertThat(check.isAvailable()).isTrue();
        verify(connection).close();
    }

    @Test
    void returnsDownAndReleasesAnInvalidConnection() throws SQLException {
        when(connection.isValid(2)).thenReturn(false);

        assertThat(check.isAvailable()).isFalse();
        verify(connection).close();
    }

    @Test
    void returnsDownWhenThePoolCannotSupplyAConnection() throws SQLException {
        when(dataSource.getConnection()).thenThrow(new SQLException());

        assertThat(check.isAvailable()).isFalse();
    }

    @Test
    void releasesTheConnectionWhenValidationFails() throws SQLException {
        when(connection.isValid(2)).thenThrow(new SQLException());

        assertThat(check.isAvailable()).isFalse();
        verify(connection).close();
    }
}
