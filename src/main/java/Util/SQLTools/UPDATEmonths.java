package Util.SQLTools;

import java.sql.PreparedStatement;
import java.sql.SQLException;

public class UPDATEmonths {
    private SQLConnect connection = new SQLConnect();
    private boolean result;

    public boolean edit(Integer months, String name){

        try{

            String sql = "UPDATE subs "
                    + "SET months_subbed='"+months+"' "
                    + "WHERE username = '" + name + "'";
            PreparedStatement stmnt = connection.connect().prepareStatement(sql);
            stmnt.execute();

            result = true;
        }catch (SQLException e) {
            result = false;
            e.printStackTrace();
        } finally {
            connection.disconnect();
        }

        return result;

    }
}
