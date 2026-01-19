package com.antovdv.centro_computo;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class database {
    private static final String URL = "jdbc:mysql://localhost:3306/centro_computo";
    private static final String USER = "root";
    private static final String PASS = "";

    public static Connection getConection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASS);
    }


}
