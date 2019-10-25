package Util.SQLTools;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class EDITSubname {

    private Connection conn;
    private boolean result;

    public boolean edit(String userName, String newSubName, Connection connection) {

        this.conn = connection;

        try{

            String sql = "UPDATE subs SET subname='"+newSubName+"' WHERE username = '" + userName + "'";
            PreparedStatement stmnt = conn.prepareStatement(sql);
            stmnt.execute();

            result = true;
        }catch (SQLException e) {
            result = false;
            e.printStackTrace();
        }

        return result;

    }

}
