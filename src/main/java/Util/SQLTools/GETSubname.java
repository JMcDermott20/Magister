package Util.SQLTools;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class GETSubname {
    private Connection conn;
    private String subname;

    public String get(String username, Connection connection) throws SQLException {

        this.conn = connection;
        Statement stmnt = null;
        try {
            String sql = "SELECT SUBNAME FROM subs where USERNAME = '" +username+"'";

            stmnt = conn.createStatement();
            ResultSet rs = stmnt.executeQuery(sql);

            if (!rs.next() ) {
                subname = "ERROR";
            }
            else{
                subname = rs.getString("SUBNAME");
            }
        } catch (SQLException e) {
            System.out.println("Unable to create statement");
        }
        finally{
            stmnt.close();
            return subname;
        }


    }

}
