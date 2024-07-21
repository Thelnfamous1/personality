package io.blodhgarm.personality.utils;

import org.apache.commons.collections4.map.ListOrderedMap;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.util.List;

public class ReflectionUtils {

    private static final VarHandle INSERT_ORDER_ACCESS;

    static {
        try {
            Field field = ListOrderedMap.class.getDeclaredField("insertOrder");

            field.setAccessible(true);

            INSERT_ORDER_ACCESS = MethodHandles.privateLookupIn(ListOrderedMap.class, MethodHandles.lookup()).unreflectVarHandle(field);
        }
        catch (NoSuchFieldException | SecurityException exception){ throw new RuntimeException("Attempting to get the Field [insertOrder] within [ListOrderedMap] has failed!", exception); }
        catch (IllegalAccessException exception){ throw new RuntimeException("Attempting to get the Field [insertOrder] unreflected VarHandle within [ListOrderedMap] has failed!", exception); }
    }

    public static <K, V> List<K> getMapInsertOrder(ListOrderedMap<K, V> map){
        return ((List<K>)INSERT_ORDER_ACCESS.get(map));
    }

}
