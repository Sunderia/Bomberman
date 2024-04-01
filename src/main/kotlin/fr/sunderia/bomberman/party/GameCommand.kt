package fr.sunderia.bomberman.party

import net.kyori.adventure.text.Component.text
import net.minestom.server.MinecraftServer
import net.minestom.server.command.builder.Command
import net.minestom.server.entity.Player

class GameCommand: Command("game") {
    init {
        val manager = MinecraftServer.getInstanceManager()
        addSyntax({ sender, _ ->
            if(sender !is Player) return@addSyntax
            val party = Party.getParty(sender)
            if(party != null && party.playerList.size != 0) {
                party.warp(Game.createGame(manager))
                //Funny text to send
                return@addSyntax
            }

            var game = Game.getNonFilledGames()
            if(game == null) {
                game = Game.getGame(Game.createGame(manager))!!
            }

            if(party != null) party.warp(game.instance)
            else {
                sender.instance = game.instance
                sender.sendMessage(text("Successfully sent you to a Bomberman game"))
            }
        })
    }
}