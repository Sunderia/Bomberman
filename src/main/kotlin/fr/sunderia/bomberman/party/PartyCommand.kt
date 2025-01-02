package fr.sunderia.bomberman.party

import net.kyori.adventure.text.Component.text
import fr.sunderia.bomberman.party.Party.Companion.createParty
import fr.sunderia.bomberman.party.Party.Companion.removePlayerFromParty
import fr.sunderia.bomberman.party.Party.Companion.getParty
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.command.CommandSender
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.CommandContext
import net.minestom.server.command.builder.arguments.ArgumentEnum
import net.minestom.server.command.builder.arguments.ArgumentType
import net.minestom.server.entity.Player

class PartyCommand: Command("party", "p") {

    private enum class SubCommand {
        INVITE,
        JOIN,
        LEAVE,
        LIST,
        CREATE
    }

    init {
        val subcommand = ArgumentType.Enum("subcommand", SubCommand::class.java)
            .setFormat(ArgumentEnum.Format.LOWER_CASED)
        val playerName = ArgumentType.Entity("target").onlyPlayers(true).singleEntity(true)

        subcommand.setCallback { sender: CommandSender, exception ->
            sender.sendMessage(
                text("Invalid subcommand ", NamedTextColor.RED)
                    .append(text(exception.input, NamedTextColor.WHITE))
                    .append(text("!"))
            )
        }

        setDefaultExecutor { sender, context ->
            sender.sendMessage(
                text(
                    "Usage: /${context.commandName} <subcommand> [player]",
                    NamedTextColor.RED
                )
            )
        }

        addSyntax({ sender: CommandSender, context: CommandContext ->
            if(sender !is Player) return@addSyntax
            when(context.get(subcommand)) {
                SubCommand.LIST -> {
                    val party = getParty(sender)
                    if(party == null) {
                        sender.sendMessage(text("You are not in any party.").color(NamedTextColor.RED))
                        return@addSyntax
                    }

                    sender.sendMessage(text {
                        it.append(text("Party:")).appendNewline()
                        it.append(text("  - "))
                            .append(text(party.host.username).color(NamedTextColor.RED))
                            .appendNewline()
                        party.playerList.forEach { player ->
                            it.append(text("  - ${player.username}")).appendNewline()
                        }
                    })
                }
                SubCommand.CREATE -> {
                    createParty(sender)
                    sender.sendMessage(text(" > Successfully created a party"))
                }
                SubCommand.LEAVE -> {
                    val party = getParty(sender)
                    if(party == null) {
                        sender.sendMessage(text("You are not in any party.").color(NamedTextColor.RED))
                        return@addSyntax
                    }
                    removePlayerFromParty(sender)
                }
                else -> {}
            }
        }, subcommand)

        addSyntax({ sender: CommandSender, context: CommandContext ->
            if(sender !is Player) return@addSyntax
            val find = context.get(playerName).find(sender)
            if(find.isEmpty()) return@addSyntax
            val target = find[0] as Player
            val subCommandType = context.get(subcommand)
            if(subCommandType == SubCommand.INVITE) {
                val party = getParty(sender)
                if(party == null) {
                    sender.sendMessage(text("You are not in any party.").color(NamedTextColor.RED))
                    return@addSyntax
                }
                if(!target.isOnline) {
                    sender.sendMessage(text("${target.username} is not online.").color(NamedTextColor.RED))
                    return@addSyntax
                }
                party.sendInvite(target)
                sender.sendMessage(text("An invite has been sent to ${target.username}"))
            } else if(subCommandType == SubCommand.JOIN) {
                val senderParty = getParty(sender)
                if(senderParty != null) {
                    sender.sendMessage(text("You are already in a party.").color(NamedTextColor.RED))
                    return@addSyntax
                }
                val hostParty = getParty(target)
                if(hostParty == null) {
                    sender.sendMessage(text("The player ${target.username} is not in a party.").color(NamedTextColor.RED))
                    return@addSyntax
                }
                if(hostParty.invites.none { it.uuid == sender.uuid }) {
                    sender.sendMessage(text("You haven't recieved any invites from this party.").color(NamedTextColor.RED))
                    return@addSyntax
                }
                hostParty.joinParty(sender)
            }
        }, subcommand, playerName)
    }
}