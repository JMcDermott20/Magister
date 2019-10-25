package Util.SQLTools;

import java.sql.PreparedStatement;
import java.sql.SQLException;

public class ADDSubname {

    private SQLConnect connection = new SQLConnect();
    private boolean result;

    public boolean add(String username, String subName) {

        try{

            String sql = "INSERT INTO `subs` (`id`,`username`,`subname`,`months_subbed`) " +
                    "VALUES (NULL, '" + username + "', '" + subName + "', 1)";
            PreparedStatement stmnt = connection.connect().prepareStatement(sql);
            stmnt.execute();
            result = true;
        }catch (SQLException e) {
            e.printStackTrace();
            result = false;
        } finally {
            connection.disconnect();
        }

        return result;
    }

}
