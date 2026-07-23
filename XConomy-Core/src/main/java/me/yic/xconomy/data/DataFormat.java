/*
 *  This file (DataFormat.java) is a part of project XConomy
 *  Copyright (C) YiC and contributors
 *
 *  This program is free software: you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License as published by the
 *  Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 *  or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  for more details.
 *
 *  You should have received a copy of the GNU General Public License along
 *  with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package me.yic.xconomy.data;

import me.yic.xconomy.XConomyLoad;
import me.yic.xconomy.adapter.comp.CChat;
import me.yic.xconomy.info.DefaultConfig;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

public class DataFormat {

    public static boolean isint = false;
    public static int decimalPlaces = 2;  // 可配置的小数位数
    public static DecimalFormat decimalFormat;
    public static DecimalFormat decimalFormatX;
    public static BigDecimal maxNumber;
    public static RoundingMode roundingmode = RoundingMode.DOWN;
    public static String displayformat;
    public static String pluralname;
    public static String singularname;



    public static BigDecimal formatString(String am) {
        BigDecimal bigDecimal = new BigDecimal(am);
        if (isint) {
            return bigDecimal.setScale(0, roundingmode);
        } else {
            return bigDecimal.setScale(decimalPlaces, roundingmode);
        }
    }

    public static BigDecimal formatdouble(double am) {
        BigDecimal bigDecimal = BigDecimal.valueOf(am);
        if (isint) {
            return bigDecimal.setScale(0, roundingmode);
        } else {
            return bigDecimal.setScale(decimalPlaces, roundingmode);
        }
    }

    public static BigDecimal formatBigDecimal(BigDecimal am) {
        if (isint) {
            return am.setScale(0, roundingmode);
        } else {
            return am.setScale(decimalPlaces, roundingmode);
        }
    }

    public static String shown(BigDecimal am) {
        if (am.compareTo(BigDecimal.ONE) == 0) {
            return CChat.translateAlternateColorCodes('&', displayformat
                    .replace("%balance%", decimalFormat.format(am))
                    .replace("%format_balance%", getformatbalance(am))
                    .replace("%currencyname%", singularname));
        }
        return CChat.translateAlternateColorCodes('&', displayformat
                .replace("%balance%", decimalFormat.format(am))
                .replace("%format_balance%", getformatbalance(am))
                .replace("%currencyname%", pluralname));
    }


    public static String shown(double am) {
        return shown(BigDecimal.valueOf(am));
    }

    public static String PEshownf(BigDecimal am) {
        if (am.compareTo(BigDecimal.ONE) == 0) {
            return CChat.translateAlternateColorCodes('&', displayformat
                    .replace("%balance%", getformatbalance(am))
                    .replace("%format_balance%", getformatbalance(am))
                    .replace("%currencyname%", singularname));
        }
        return CChat.translateAlternateColorCodes('&', displayformat
                .replace("%balance%", getformatbalance(am))
                .replace("%format_balance%", getformatbalance(am))
                .replace("%currencyname%", pluralname));
    }

    public static boolean isMAX(BigDecimal am) {
        return am.compareTo(maxNumber) > 0;
    }

    public static void load() {
        maxNumber = setmaxnumber();
        isint = XConomyLoad.Config.INTEGER_BAL;
        decimalPlaces = XConomyLoad.Config.DECIMAL_PLACES;
        String gpoint = XConomyLoad.Config.THOUSANDS_SEPARATOR;
        
        // 更新货币名称和显示格式
        displayformat = XConomyLoad.Config.DISPLAY_FORMAT;
        pluralname = XConomyLoad.Config.PLURAL_NAME;
        singularname = XConomyLoad.Config.SINGULAR_NAME;
        
        decimalFormat = new DecimalFormat();
        decimalFormatX = new DecimalFormat();

        decimalFormatX.setMinimumFractionDigits(decimalPlaces);
        decimalFormatX.setMaximumFractionDigits(decimalPlaces);

        if (isint) {
            decimalFormat.setMinimumFractionDigits(0);
            decimalFormat.setMaximumFractionDigits(0);
        }else{
            decimalFormat.setMinimumFractionDigits(decimalPlaces);
            decimalFormat.setMaximumFractionDigits(decimalPlaces);
        }

        if (XConomyLoad.Config.ROUNDING_MODE == 1){
            roundingmode = RoundingMode.UP;
        }

        if (gpoint != null && gpoint.length() == 1) {
            DecimalFormatSymbols spoint = new DecimalFormatSymbols();
            spoint.setGroupingSeparator(gpoint.charAt(0));
            decimalFormat.setDecimalFormatSymbols(spoint);
            decimalFormatX.setDecimalFormatSymbols(spoint);
        }

        XConomyLoad.Config.PAYMENT_TAX = setpaymenttax();
    }


    private static BigDecimal setmaxnumber() {
        String maxn = XConomyLoad.Config.MAX_NUMBER;
        BigDecimal defaultmaxnumber = new BigDecimal("10000000000000000");
        if (maxn == null) {
            return defaultmaxnumber;
        }
        if (maxn.length() > 17) {
            return defaultmaxnumber;
        }
        BigDecimal mnumber = new BigDecimal(maxn);
        if (mnumber.compareTo(defaultmaxnumber) >= 0) {
            return defaultmaxnumber;
        } else {
            return mnumber;
        }
    }


    private static BigDecimal setpaymenttax() {
        double pt = DefaultConfig.config.getDouble("Settings.payment-tax");
        if (pt < 0.0) {
            pt = 0.0;
        }
        return BigDecimal.valueOf(pt).add(BigDecimal.ONE);
    }

    private static String getformatbalance(BigDecimal bal) {
        if (XConomyLoad.Config.FORMAT_BALANCE != null) {
            if (bal.compareTo(XConomyLoad.Config.FORMAT_BALANCE.get(0)) < 0) {
                return decimalFormat.format(bal);
            }
            BigDecimal x = BigDecimal.ZERO;
            for (BigDecimal b : XConomyLoad.Config.FORMAT_BALANCE) {
                if (bal.compareTo(b) >= 0) {
                    x = b;
                } else {
                    break;
                }
            }
            BigDecimal aa = bal.divide(x, 3, roundingmode);
            return decimalFormatX.format(aa) + XConomyLoad.Config.FORMAT_BALANCE_C.get(x);
        } else {
            return decimalFormat.format(bal);
        }
    }
}
