package com.example.utopia.domain

import com.example.utopia.data.models.Structure
import com.example.utopia.util.Constants

fun writeToGrid(grid: Array<Array<String?>>, structure: Structure): Array<Array<String?>> {
    val newGrid = grid.map { it.clone() }.toTypedArray()
    val spec = structure.spec
    val minX = (structure.x / Constants.TILE_SIZE).toInt()
    val minY = ((structure.y - spec.worldHeight) / Constants.TILE_SIZE).toInt()
    val maxX = ((structure.x + spec.worldWidth - 1f) / Constants.TILE_SIZE).toInt()
    val maxY = ((structure.y - 1f) / Constants.TILE_SIZE).toInt()

    for (ix in minX..maxX) {
        for (iy in minY..maxY) {
            if (ix in 0 until Constants.MAP_TILES_W && iy in 0 until Constants.MAP_TILES_H) {
                newGrid[ix][iy] = structure.id
            }
        }
    }
    return newGrid
}

fun clearFromGrid(grid: Array<Array<String?>>, structure: Structure): Array<Array<String?>> {
    val newGrid = grid.map { it.clone() }.toTypedArray()
    val spec = structure.spec
    val minX = (structure.x / Constants.TILE_SIZE).toInt()
    val minY = ((structure.y - spec.worldHeight) / Constants.TILE_SIZE).toInt()
    val maxX = ((structure.x + spec.worldWidth - 1f) / Constants.TILE_SIZE).toInt()
    val maxY = ((structure.y - 1f) / Constants.TILE_SIZE).toInt()

    for (ix in minX..maxX) {
        for (iy in minY..maxY) {
            if (ix in 0 until Constants.MAP_TILES_W && iy in 0 until Constants.MAP_TILES_H) {
                if (newGrid[ix][iy] == structure.id) {
                    newGrid[ix][iy] = null
                }
            }
        }
    }
    return newGrid
}
