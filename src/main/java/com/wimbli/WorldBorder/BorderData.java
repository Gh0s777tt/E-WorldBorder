package com.wimbli.WorldBorder;

import java.util.EnumSet;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

public class BorderData {
    // the main data interacted with
    private double  x          = 0;
    private double  z          = 0;
    private int     radiusX    = 0;
    private int     radiusZ    = 0;
    private Boolean shapeRound = null;
    private boolean wrapping   = false;

    // some extra data kept handy for faster border checks
    private double  maxX;
    private double  minX;
    private double  maxZ;
    private double  minZ;
    private double  radiusXSquared;
    private double  radiusZSquared;
    private double  DefiniteRectangleX;
    private double  DefiniteRectangleZ;
    private double  radiusSquaredQuotient;


    public BorderData(double x, double z, int radiusX, int radiusZ,
            Boolean shapeRound, boolean wrap) {
        setData(x, z, radiusX, radiusZ, shapeRound, wrap);
    }


    public BorderData(double x, double z, int radiusX, int radiusZ) {
        setData(x, z, radiusX, radiusZ, null);
    }


    public BorderData(double x, double z, int radiusX, int radiusZ,
            Boolean shapeRound) {
        setData(x, z, radiusX, radiusZ, shapeRound);
    }


    public BorderData(double x, double z, int radius) {
        setData(x, z, radius, null);
    }


    public BorderData(double x, double z, int radius, Boolean shapeRound) {
        setData(x, z, radius, shapeRound);
    }


    public final void setData(double x, double z, int radiusX, int radiusZ,
            Boolean shapeRound, boolean wrap) {
        this.x = x;
        this.z = z;
        this.shapeRound = shapeRound;
        this.wrapping = wrap;
        this.setRadiusX(radiusX);
        this.setRadiusZ(radiusZ);
    }


    public final void setData(double x, double z, int radiusX, int radiusZ,
            Boolean shapeRound) {
        setData(x, z, radiusX, radiusZ, shapeRound, false);
    }


    public final void setData(double x, double z, int radius,
            Boolean shapeRound) {
        setData(x, z, radius, radius, shapeRound, false);
    }


    public BorderData copy() {
        return new BorderData(x, z, radiusX, radiusZ, shapeRound, wrapping);
    }


    public double getX() {
        return x;
    }


    public void setX(double x) {
        this.x = x;
        this.maxX = x + radiusX;
        this.minX = x - radiusX;
    }


    public double getZ() {
        return z;
    }


    public void setZ(double z) {
        this.z = z;
        this.maxZ = z + radiusZ;
        this.minZ = z - radiusZ;
    }


    public int getRadiusX() {
        return radiusX;
    }


    public int getRadiusZ() {
        return radiusZ;
    }


    public void setRadiusX(int radiusX) {
        this.radiusX = radiusX;
        this.maxX = x + radiusX;
        this.minX = x - radiusX;
        this.radiusXSquared = (double) radiusX * (double) radiusX;
        this.radiusSquaredQuotient = this.radiusXSquared / this.radiusZSquared;
        this.DefiniteRectangleX = Math.sqrt(.5 * this.radiusXSquared);
    }


    public void setRadiusZ(int radiusZ) {
        this.radiusZ = radiusZ;
        this.maxZ = z + radiusZ;
        this.minZ = z - radiusZ;
        this.radiusZSquared = (double) radiusZ * (double) radiusZ;
        this.radiusSquaredQuotient = this.radiusXSquared / this.radiusZSquared;
        this.DefiniteRectangleZ = Math.sqrt(.5 * this.radiusZSquared);
    }


    // backwards-compatible methods from before elliptical/rectangular shapes
    // were supported
    /**
     * @deprecated Replaced by {@link #getRadiusX()} and {@link #getRadiusZ()};
     * this method now returns an average of those two values and is thus
     * imprecise
     */
    public int getRadius() {
        return (radiusX + radiusZ) / 2;  // average radius; not great, but
                                         // probably best for backwards
                                         // compatibility
    }


    public void setRadius(int radius) {
        setRadiusX(radius);
        setRadiusZ(radius);
    }


    public Boolean getShape() {
        return shapeRound;
    }


    public void setShape(Boolean shapeRound) {
        this.shapeRound = shapeRound;
    }


    public boolean getWrapping() {
        return wrapping;
    }


    public void setWrapping(boolean wrap) {
        this.wrapping = wrap;
    }


    @Override
    public String toString() {
        return "radius "
                + ((radiusX == radiusZ) ? radiusX : radiusX + "x" + radiusZ)
                + " at X: " + Config.coord.format(x) + " Z: "
                + Config.coord.format(z)
                + (shapeRound != null ? (" (shape override: "
                        + Config.ShapeName(shapeRound.booleanValue()) + ")")
                        : "")
                + (wrapping ? (" (wrapping)") : "");
    }


    // This algorithm of course needs to be fast, since it will be run very
    // frequently
    public boolean insideBorder(double xLoc, double zLoc, boolean round) {
        // if this border has a shape override set, use it
        if (shapeRound != null)
            round = shapeRound.booleanValue();

        // square border
        if (!round)
            return !(xLoc < minX || xLoc > maxX || zLoc < minZ || zLoc > maxZ);

        // round border
        else {
            // elegant round border checking algorithm is from rBorder by Reil
            // with almost no changes, all credit to him for it
            double X = Math.abs(x - xLoc);
            double Z = Math.abs(z - zLoc);

            if (X < DefiniteRectangleX && Z < DefiniteRectangleZ)
                return true;	// Definitely inside
            else if (X >= radiusX || Z >= radiusZ)
                return false;	// Definitely outside
            else if (X * X + Z * Z * radiusSquaredQuotient < radiusXSquared)
                return true;	// After further calculation, inside
            else
                return false;	// Apparently outside, then
        }
    }


    public boolean insideBorder(double xLoc, double zLoc) {
        return insideBorder(xLoc, zLoc, Config.ShapeRound());
    }


    public boolean insideBorder(Location loc) {
        return insideBorder(loc.getX(), loc.getZ(), Config.ShapeRound());
    }


    public boolean insideBorder(CoordXZ coord, boolean round) {
        return insideBorder(coord.x, coord.z, round);
    }


    public boolean insideBorder(CoordXZ coord) {
        return insideBorder(coord.x, coord.z, Config.ShapeRound());
    }


    public Location correctedPosition(Location loc, boolean round,
            boolean flying) {
        // if this border has a shape override set, use it
        if (shapeRound != null)
            round = shapeRound.booleanValue();

        double xLoc = loc.getX();
        double zLoc = loc.getZ();
        double yLoc = loc.getY();

        // square border
        if (!round) {
            if (wrapping) {
                if (xLoc <= minX)
                    xLoc = maxX - Config.KnockBack();
                else if (xLoc >= maxX)
                    xLoc = minX + Config.KnockBack();
                if (zLoc <= minZ)
                    zLoc = maxZ - Config.KnockBack();
                else if (zLoc >= maxZ)
                    zLoc = minZ + Config.KnockBack();
            }
            else {
                if (xLoc <= minX)
                    xLoc = minX + Config.KnockBack();
                else if (xLoc >= maxX)
                    xLoc = maxX - Config.KnockBack();
                if (zLoc <= minZ)
                    zLoc = minZ + Config.KnockBack();
                else if (zLoc >= maxZ)
                    zLoc = maxZ - Config.KnockBack();
            }
        }

        // round border
        else {
            // algorithm originally from:
            // http://stackoverflow.com/questions/300871/best-way-to-find-a-point-on-a-circle-closest-to-a-given-point
            // modified by Lang Lukas to support elliptical border shape

            // Transform the ellipse to a circle with radius 1 (we need to
            // transform the point the same way)
            double dX = xLoc - x;
            double dZ = zLoc - z;
            double dU = Math.sqrt(dX * dX + dZ * dZ); // distance of the
                                                      // untransformed point
                                                      // from the center
            double dT = Math
                    .sqrt(dX * dX / radiusXSquared + dZ * dZ / radiusZSquared); // distance
                                                                                // of
                                                                                // the
                                                                                // transformed
                                                                                // point
                                                                                // from
                                                                                // the
                                                                                // center
            double f = (1 / dT - Config.KnockBack() / dU); // "correction"
                                                           // factor for the
                                                           // distances
            if (wrapping) {
                xLoc = x - dX * f;
                zLoc = z - dZ * f;
            }
            else {
                xLoc = x + dX * f;
                zLoc = z + dZ * f;
            }
        }

        int ixLoc = Location.locToBlock(xLoc);
        int izLoc = Location.locToBlock(zLoc);

        // Make sure the chunk we're checking in is actually loaded
        Chunk tChunk = loc.getWorld().getChunkAt(CoordXZ.blockToChunk(ixLoc),
                CoordXZ.blockToChunk(izLoc));
        if (!tChunk.isLoaded())
            tChunk.load();

        yLoc = getSafeY(loc.getWorld(), ixLoc, Location.locToBlock(yLoc), izLoc,
                flying);
        if (yLoc == -1)
            return null;

        return new Location(loc.getWorld(), Math.floor(xLoc) + 0.5, yLoc,
                Math.floor(zLoc) + 0.5, loc.getYaw(), loc.getPitch());
    }


    public Location correctedPosition(Location loc, boolean round) {
        return correctedPosition(loc, round, false);
    }


    public Location correctedPosition(Location loc) {
        return correctedPosition(loc, Config.ShapeRound(), false);
    }
    
    /**
     * Set of block materials that cause damage.
     */
    private EnumSet<Material> damagingBlocks = EnumSet.of(
            Material.CACTUS,
            Material.FIRE,
            Material.LAVA,
            Material.MAGMA_BLOCK);
    

    /**
     * Determines if given material represents an open block; that is, a block
     * that is not solid allowing player to occupy the same position.
     * 
     * @param mat material of block to test
     * @return true, if block is considered open; otherwise, false.
     */
    private boolean isOpenBlock(final Material mat) {
        return mat.isBlock() && !mat.isSolid();
    }
    
    
    /**
     * Determines if given material represents a block that imparts player
     * damage.
     * 
     * @param mat material of block to test
     * @return true, if block does damage; otherwise, false.
     */
    private boolean isDamagingBlock(final Material mat) {
        return damagingBlocks.contains(mat);
    }
    
    
    /**
     * Determines if given material represents an open block that does no
     * damage (e.g. lava).
     * 
     * @param mat material of block to test
     * @return true, if block does damage; otherwise, false.
     */
    private boolean isSafeOpenBlock(final Material mat) {
        return isOpenBlock(mat) && !isDamagingBlock(mat);
    }

    
    /**
     * Determine if the given location is a safe spot, which is defined as
     * having open block at the foot level and head level of a player and
     * a non-damaging block below.
     * 
     * @param world
     * @param x
     * @param y
     * @param z
     * @param flying
     * @return true, if location is safe; otherwise, false.
     */
    private boolean isSafeSpot(World world, int x, int y, int z,
            boolean flying) {
        final Block feet = world.getBlockAt(x, y, z);
        final Block head = feet.getRelative(BlockFace.UP);
        
        boolean safe = isSafeOpenBlock(feet.getType())
                && isSafeOpenBlock(head.getType());
        if (!safe || flying)
            return false;

        final Block below = feet.getRelative(BlockFace.DOWN);
        return !isDamagingBlock(below.getType());
    }

    private static final int limBot = 0;


    // find closest safe Y position from the starting position
    private double getSafeY(World world, int X, int Y, int Z, boolean flying) {
        // artificial height limit of 127 added for Nether worlds since
        // CraftBukkit still incorrectly returns 255 for their max height,
        // leading to players sent to the "roof" of the Nether
        final boolean isNether = world
                .getEnvironment() == World.Environment.NETHER;
        int limTop = isNether ? 125 : world.getMaxHeight() - 2;
        final int highestBlockBoundary = Math
                .min(world.getHighestBlockYAt(X, Z) + 1, limTop);

        // if Y is larger than the world can be and user can fly, return Y -
        // Unless we are in the Nether, we might not want players on the roof
        if (flying && Y > limTop && !isNether)
            return (double) Y;

        // make sure Y values are within the boundaries of the world.
        if (Y > limTop) {
            if (isNether)
                Y = limTop; // because of the roof, the nether can not rely on
                            // highestBlockBoundary, so limTop has to be used
            else {
                if (flying)
                    Y = limTop;
                else
                    Y = highestBlockBoundary; // there will never be a save
                                              // block to stand on for Y values
                                              // > highestBlockBoundary
            }
        }
        if (Y < limBot)
            Y = limBot;

        // for non Nether worlds we don't need to check upwards to the
        // world-limit, it is enough to check up to the highestBlockBoundary,
        // unless player is flying
        if (!isNether && !flying)
            limTop = highestBlockBoundary;
        // Expanding Y search method adapted from Acru's code in the Nether
        // plugin

        for (int y1 = Y, y2 = Y; (y1 > limBot) || (y2 < limTop); y1--, y2++) {
            // Look below.
            if (y1 > limBot) {
                if (isSafeSpot(world, X, y1, Z, flying))
                    return (double) y1;
            }

            // Look above.
            if (y2 < limTop && y2 != y1) {
                if (isSafeSpot(world, X, y2, Z, flying))
                    return (double) y2;
            }
        }

        return -1.0;	// no safe Y location?!?!? Must be a rare spot in a
                    	// Nether world or something
    }


    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        else if (obj == null || obj.getClass() != this.getClass())
            return false;

        BorderData test = (BorderData) obj;
        return test.x == this.x && test.z == this.z
                && test.radiusX == this.radiusX && test.radiusZ == this.radiusZ;
    }


    @Override
    public int hashCode() {
        return (((int) (this.x * 10) << 4) + (int) this.z + (this.radiusX << 2)
                + (this.radiusZ << 3));
    }
}
