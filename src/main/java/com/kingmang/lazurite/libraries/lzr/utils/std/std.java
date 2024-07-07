package com.kingmang.lazurite.libraries.lzr.utils.std;

import com.kingmang.lazurite.core.Arguments;
import com.kingmang.lazurite.core.Function;
import com.kingmang.lazurite.exceptions.LzrException;
import com.kingmang.lazurite.libraries.Keyword;
import com.kingmang.lazurite.core.Types;
import com.kingmang.lazurite.libraries.Library;
import com.kingmang.lazurite.console.Console;
import com.kingmang.lazurite.runtime.values.*;
import com.kingmang.lazurite.runtime.values.LzrValue;
import com.kingmang.lazurite.runtime.Variables;
import com.kingmang.lazurite.utils.ValueUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Supplier;

public class std implements Library {
    @Override
    public void init(){
        final LzrMap std = new LzrMap(2);
        final LzrMap arrayDeque = new LzrMap(4);

        std.set("flatmap", new flatmap());
        std.set("thread", new thread());


        arrayDeque.set("add", LzrArrayDeque::addToQueue);
        arrayDeque.set("remove", LzrArrayDeque::remove);
        arrayDeque.set("size", LzrArrayDeque::sizeQueue);
        arrayDeque.set("toArray", LzrArrayDeque::toArray);

        Variables.define("arrayDeque", arrayDeque);
        Variables.define("std", std);

        Keyword.put("hashMap", mapFunction(HashMap::new));
        Keyword.put("linkedHashMap", mapFunction(LinkedHashMap::new));
        Keyword.put("concurrentHashMap", mapFunction(ConcurrentHashMap::new));
        Keyword.put("treeMap", sortedMapFunction(TreeMap::new, TreeMap::new));
        Keyword.put("concurrentSkipListMap", sortedMapFunction(ConcurrentSkipListMap::new, ConcurrentSkipListMap::new));

    }


    public final static class flatmap implements Function {

        @Override
        public LzrValue execute(LzrValue... args) {
            Arguments.check(2, args.length);
            if (args[0].type() != Types.ARRAY) {
                throw new LzrException("TypeExeption ", "Array expected in first argument");
            }
            final Function mapper = ValueUtils.consumeFunction(args[1], 1);
            return flatMapArray((LzrArray) args[0], mapper);
        }

        private LzrValue flatMapArray(LzrArray array, Function mapper) {
            final List<LzrValue> values = new ArrayList<>();
            final int size = array.size();
            for (int i = 0; i < size; i++) {
                final LzrValue inner = mapper.execute(array.get(i));
                if (inner.type() != Types.ARRAY) {
                    throw new LzrException("TypeExeption ", "Array expected " + inner);
                }
                for (LzrValue value : (LzrArray) inner) {
                    values.add(value);
                }
            }
            return new LzrArray(values);
        }
    }
    public static final class thread implements Function {

        @Override
        public LzrValue execute(LzrValue... args) {
            Arguments.checkAtLeast(1, args.length);

            Function body;
            if (args[0].type() == Types.FUNCTION) {
                body = ((LzrFunction) args[0]).getValue();
            } else {
                body = Keyword.get(args[0].asString());
            }

            final LzrValue[] params = new LzrValue[args.length - 1];
            if (params.length > 0) {
                System.arraycopy(args, 1, params, 0, params.length);
            }

            final Thread thread = new Thread(() -> body.execute(params));
            thread.setUncaughtExceptionHandler(Console::handleException);
            thread.start();
            return LzrNumber.ZERO;
        }
    }

    public static final class LzrArrayDeque {
        static Deque<LzrValue> queue = new ArrayDeque<>();

        public static LzrValue addToQueue(LzrValue[] args) {
            Arguments.check(1,  args.length);
            queue.add(args[0]);
            return LzrNumber.ZERO;

        }

        public static LzrValue toArray(LzrValue[] args) {
            List<LzrValue> array_list = new ArrayList<>(queue);
            return new LzrArray(array_list);

        }

        public static LzrValue remove(LzrValue[] args) {
            Arguments.check(1,  args.length);
            queue.remove(args[0]);
            return LzrNumber.ZERO;
        }

        public static LzrValue sizeQueue(LzrValue[] args) {
            Arguments.check(1,  args.length);
            return new LzrNumber(queue.size());
        }



    }


    private Function mapFunction(final Supplier<Map<LzrValue, LzrValue>> mapSupplier) {
        return (args) -> {
            Arguments.checkOrOr(0, 1, args.length);
            final Map<LzrValue, LzrValue> map = mapSupplier.get();
            if (args.length == 1) {
                if (args[0].type() == Types.MAP) {
                    map.putAll(((LzrMap) args[0]).getMap());
                } else {
                    throw new LzrException("TypeException ","Map expected in first argument");
                }
            }
            return new LzrMap(map);
        };
    }

    private Function sortedMapFunction(final Supplier<SortedMap<LzrValue, LzrValue>> mapSupplier,
                                       final java.util.function.Function<
                                               Comparator<? super LzrValue>,
                                               SortedMap<LzrValue, LzrValue>> comparatorToMapFunction) {
        return (args) -> {
            Arguments.checkRange(0, 2, args.length);
            final SortedMap<LzrValue, LzrValue> map;
            switch (args.length) {
                case 0: // treeMap()
                    map = mapSupplier.get();
                    break;
                case 1: // treeMap(map) || treeMap(comparator)
                    if (args[0].type() == Types.MAP) {
                        map = mapSupplier.get();
                        map.putAll(((LzrMap) args[0]).getMap());
                    } else if (args[0].type() == Types.FUNCTION) {
                        final Function comparator = ValueUtils.consumeFunction(args[0], 0);
                        map = comparatorToMapFunction.apply((o1, o2) -> comparator.execute(o1, o2).asInt());
                    } else {
                        throw new LzrException("TypeException ","Map or comparator function expected in first argument");
                    }
                    break;
                case 2: // treeMap(map, comparator)
                    if (args[0].type() != Types.MAP) {
                        throw new LzrException("TypeException ", "Map expected in first argument");
                    }
                    final Function comparator = ValueUtils.consumeFunction(args[1], 1);
                    map = comparatorToMapFunction.apply((o1, o2) -> comparator.execute(o1, o2).asInt());
                    map.putAll(((LzrMap) args[0]).getMap());
                    break;
                default:
                    throw new IllegalStateException();
            }
            return new LzrMap(map);
        };
    }

}
