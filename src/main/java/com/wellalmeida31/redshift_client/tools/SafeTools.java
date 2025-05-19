package com.wellalmeida31.redshift_client.tools;

import com.wellalmeida31.redshift_client.persistence.RedshiftFunctionalJdbc;

import java.sql.SQLException;

import static java.util.Objects.requireNonNull;

public final class SafeTools {

    private SafeTools(){}

    public static <T> T supplierElseSafe(RedshiftFunctionalJdbc.SQLSupplier<? extends T> supplier, T defaultValue) {
        var r = runSafe(requireNonNull(supplier, "supplier cannot be null"));
        return (r != null) ? r : defaultValue;
    }

    private static <T> T runSafe(RedshiftFunctionalJdbc.SQLSupplier<? extends T> supplier) {
        try{
            return supplier.get();
        } catch (SQLException t) {
            return null;
        }
    }
}
