package Util.SQLTools;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class GetLogin0Auth {

    private Connection conn;
    private String token;

    public String getAuth(Connection connection) throws SQLException {

        this.conn = connection;
        Statement stmnt = null;
        try {
            String sql = "SELECT login0Auth FROM TwitchSettings";
            stmnt = conn.createStatement();
            ResultSet rs = stmnt.executeQuery(sql);

            if (!rs.next() ) {
                token = "err";
                rs.close();
            }
            else{
                token = rs.getString("login0Auth");
                rs.close();
            }
        } catch (SQLException e) {

            System.out.println(e.getLocalizedMessage());
        }
        finally{
            stmnt.close();
            return token;
        }


    }

}
