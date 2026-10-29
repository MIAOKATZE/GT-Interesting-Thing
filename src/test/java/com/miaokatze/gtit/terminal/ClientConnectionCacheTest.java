package com.miaokatze.gtit.terminal;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.miaokatze.gtit.lottery.LotteryClientData;
import com.miaokatze.gtit.mail.MailClientData;
import com.miaokatze.gtit.signin.SignInClientData;
import com.miaokatze.gtit.signin.SignInReward;
import com.miaokatze.gtit.trade.NekoClientBalances;

/** Every mutable cache field returns to its initial value on a connection boundary. */
public final class ClientConnectionCacheTest {

    public static Map<Field, Object> seedPreviousConnection() throws Exception {
        Map<Field, Object> defaults = new LinkedHashMap<>();
        for (Class<?> type : new Class<?>[] { MailClientData.class, SignInClientData.class,
            LotteryClientData.class, NekoClientBalances.class }) {
            for (Field field : type.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) continue;
                field.setAccessible(true);
                defaults.put(field, field.get(null));
                Class<?> fieldType = field.getType();
                Object old;
                if (fieldType == int.class) old = 99;
                else if (fieldType == long.class) old = 99L;
                else if (fieldType == double.class) old = 99d;
                else if (fieldType == boolean.class) old = true;
                else if (fieldType == String.class) old = "old-connection";
                else if (Map.class.isAssignableFrom(fieldType)) old = new HashMap<>(Collections.singletonMap("old", 9));
                else if (List.class.isAssignableFrom(fieldType)) old = new ArrayList<>(Collections.singletonList("old"));
                else if (Set.class.isAssignableFrom(fieldType)) old = new HashSet<>(Collections.singleton("old"));
                else if (fieldType == SignInReward.class) old = new SignInReward("neko", 99, Collections.emptyList());
                else throw new AssertionError("Uncovered cache field: " + field);
                field.set(null, old);
            }
        }
        return defaults;
    }

    public static void verifyCleared(Map<Field, Object> defaults) throws Exception {
        for (Map.Entry<Field, Object> entry : defaults.entrySet()) {
            Object actual = entry.getKey().get(null);
            if (entry.getKey().getName().equals("dataVersion")) {
                if (((Long) actual) <= 99) throw new AssertionError("mail view must observe reset revision");
            } else if (!java.util.Objects.equals(entry.getValue(), actual)) {
                throw new AssertionError("Old connection cache survived: " + entry.getKey().getName());
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Map<Field, Object> defaults = seedPreviousConnection();
        MailClientData.resetSession();
        SignInClientData.resetSession();
        LotteryClientData.resetSession();
        NekoClientBalances.updateBalances(Collections.emptyMap());
        verifyCleared(defaults);
        System.out.println("ClientConnectionCacheTest: all " + defaults.size() + " mutable client cache fields reset");
    }
}
