package Util;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class GetApiClientID {

    private Connection conn;
    private String token;

    public String getAuth(Connection connection) throws SQLException {

        this.conn = connection;
        Statement stmnt = null;
        try {
            String sql = "SELECT apiClientID FROM TwitchSettings";

            stmnt = conn.createStatement();
            ResultSet rs = stmnt.executeQuery(sql);

            if (!rs.next() ) {
                token = "err";
                rs.close();
            }
            else{
                token = rs.getString("apiClientID");
                rs.close();
            }
        } catch (SQLException e) {
            System.out.println("Unable to create statement");
        }
        finally{
            stmnt.close();
            return token;
        }


    }

}
