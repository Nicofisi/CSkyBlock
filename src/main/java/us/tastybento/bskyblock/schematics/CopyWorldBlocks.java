package us.tastybento.bskyblock.schematics;


import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.util.Vector;

/**
 * Small util to load and save structures<br>
 * Currently tested with 1.10 and 1.11
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class CopyWorldBlocks {
    
    private static String version = Bukkit.getServer().getClass().getPackage().getName().replace(".", ",").split(",")[3];
    private static Class blockPositionClass;
    
    private static Constructor blockPositionConstructor;
    private static Class worldServerClass;
    private static Class craftWorldClass;
    private static Method getHandleMethod;
    private static Class minecraftServerClass;
    private static Method getMinecraftServerMethod;
    private static Class definedStructureManagerClass;
    private static Method getStructureManagerMethod;
    private static Class definedStructureClass;
    private static Class minecraftKeyClass;
    private static Constructor minecraftKeyConstructor;
    private static Method getStructureMethod;
    private static Class blocksClass;
    private static Class blockClass;
    private static Class worldClass;
    private static Object structureVoidBlock;
    private static Method setPosMethod;
    private static Method setAuthorMethod;
    private static Method saveMethod;
    private static Method loadInfoMethod;
    private static Class definedStructureInfoClass;
    private static Constructor definedStructureInfoConstructor;
    private static Class enumBlockMirrorClass;
    private static Method enumBlockMirrorValueOfMethod;
    private static Method mirrorMethod;
    private static Class enumBlockRotationClass;
    private static Method enumBlockRotationValueOfMethod;
    private static Method rotationMethod;
    private static Method ignoreEntitiesMethod;
    private static Method loadMethod;
    
    static {
        try {
            String name;
            blockPositionClass = Class.forName("net.minecraft.server." + version + ".BlockPosition");
            blockPositionConstructor = blockPositionClass.getConstructor(int.class, int.class, int.class);
            worldServerClass = Class.forName("net.minecraft.server." + version + ".WorldServer");
            craftWorldClass = Class.forName("org.bukkit.craftbukkit." + version + ".CraftWorld");
            getHandleMethod = craftWorldClass.getMethod("getHandle");
            minecraftServerClass = Class.forName("net.minecraft.server." + version + ".MinecraftServer");
            getMinecraftServerMethod = worldServerClass.getMethod("getMinecraftServer");
            definedStructureManagerClass = Class.forName("net.minecraft.server." + version + ".DefinedStructureManager");
            getStructureManagerMethod = worldServerClass.getMethod("y"); //PAIL: rename getWorldServer
            definedStructureClass = Class.forName("net.minecraft.server." + version + ".DefinedStructure");
            minecraftKeyClass = Class.forName("net.minecraft.server." + version + ".MinecraftKey");
            minecraftKeyConstructor = minecraftKeyClass.getConstructor(String.class);
            getStructureMethod = definedStructureManagerClass.getMethod("a", minecraftServerClass, minecraftKeyClass); // PAIL: rename getStructure
            blocksClass = Class.forName("net.minecraft.server." + version + ".Blocks");
            blockClass = Class.forName("net.minecraft.server." + version + ".Block");
            structureVoidBlock = blocksClass.getField("dj").get(null); // PAIL: rename STRUCTURE_VOID
            worldClass = Class.forName("net.minecraft.server." + version + ".World");
            setPosMethod = definedStructureClass.getMethod("a", worldClass, blockPositionClass, blockPositionClass, boolean.class, blockClass); // PAIL: rename setPos
            setAuthorMethod = definedStructureClass.getMethod("a", String.class); // PAIL: rename setAuthor
            // PAIL: rename save, 1.10: d, 1.11 c, 1.12 c
            name = "d";
            if (version.startsWith("v1_11") || version.startsWith("v1_12")) {
                name = "c";
            }
            saveMethod = definedStructureManagerClass.getMethod(name, minecraftServerClass, minecraftKeyClass);
            loadInfoMethod = definedStructureManagerClass.getMethod("b", minecraftServerClass, minecraftKeyClass); // PAIL: rename loadInfo
            definedStructureInfoClass = Class.forName("net.minecraft.server." + version + ".DefinedStructureInfo");
            definedStructureInfoConstructor = definedStructureInfoClass.getConstructor();
            enumBlockMirrorClass = Class.forName("net.minecraft.server." + version + ".EnumBlockMirror");
            enumBlockMirrorValueOfMethod = enumBlockMirrorClass.getMethod("valueOf", String.class);
            mirrorMethod = definedStructureInfoClass.getMethod("a", enumBlockMirrorClass);// PAIL: rename mirror
            enumBlockRotationClass = Class.forName("net.minecraft.server." + version + ".EnumBlockRotation");
            enumBlockRotationValueOfMethod = enumBlockRotationClass.getMethod("valueOf", String.class);
            rotationMethod = definedStructureInfoClass.getMethod("a", enumBlockRotationClass); // PAIL: rename rotation
            ignoreEntitiesMethod = definedStructureInfoClass.getMethod("a", boolean.class); // PAIL: rename ignoreEntities
            loadMethod = definedStructureClass.getMethod("a", worldClass, blockPositionClass, definedStructureInfoClass); // PAIL: rename load
        } catch (ClassNotFoundException | NoSuchMethodException | NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Saves a structure
     *
     * @param start           the start location
     * @param size            the size
     * @param name            the name
     * @param author          the author
     * @param includeEntities if entities should be included in the structure
     * @return if is was successful
     */
    public static boolean save(Location start, Vector size, String name, String author, boolean includeEntities) {
        try {
            Object startPos = blockPositionConstructor.newInstance(start.getBlockX(), start.getBlockY(), start.getBlockZ());
            Object sizePos = blockPositionConstructor.newInstance(size.getBlockX(), size.getBlockY(), size.getBlockZ());
            Object world = getHandleMethod.invoke(craftWorldClass.cast(start.getWorld()));
            Object server = getMinecraftServerMethod.invoke(world);
            Object structureManager = getStructureManagerMethod.invoke(world);
            Object key = minecraftKeyConstructor.newInstance(name);
            Object structure = getStructureMethod.invoke(structureManager, server, key);
            setPosMethod.invoke(structure, world, startPos, sizePos, includeEntities, structureVoidBlock);
            setAuthorMethod.invoke(structure, author);
            return (boolean) saveMethod.invoke(structureManager, server, key);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
        return false;
    }
    
    /**
     * Loads a structure
     *
     * @param origin          the origin location
     * @param name            the name
     * @param mirror          how the structure should be mirrored (FRONT_BACK, LEFT_RIGHT or NONE)
     * @param rotate          how the structure should be rotated (CLOCKWISE_90, CLOCKWISE_180,
     *                        COUNTERCLOCKWISE_90 or NONE)
     * @param includeEntities if entities should be included
     * @return if is was successful
     */
    public static boolean load(Location origin, String name, String mirror, String rotate, boolean includeEntities) {
        try {
            Object originPos = blockPositionConstructor.newInstance(origin.getBlockX(), origin.getBlockY(), origin.getBlockZ());
            Object world = getHandleMethod.invoke(craftWorldClass.cast(origin.getWorld()));
            Object server = getMinecraftServerMethod.invoke(world);
            Object structureManager = getStructureManagerMethod.invoke(world);
            Object key = minecraftKeyConstructor.newInstance(name);
            Object structure = loadInfoMethod.invoke(structureManager, server, key);
            if (structure == null) {
                return false;
            } else {
                Object structureInfo = definedStructureInfoConstructor.newInstance();
                mirrorMethod.invoke(structureInfo, enumBlockMirrorValueOfMethod.invoke(null, mirror));
                rotationMethod.invoke(structureInfo, enumBlockRotationValueOfMethod.invoke(null, rotate));
                ignoreEntitiesMethod.invoke(structureInfo, includeEntities);
                loadMethod.invoke(structure, world, originPos, structureInfo);
                return true;
            }
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
        return false;
    }
}