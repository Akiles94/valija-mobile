// Directory is `use-cases/` to match valija's own layer convention (CLAUDE.md: every file lives
// in a folder that names its kind). Kotlin package identifiers cannot contain a hyphen, so the
// package is `usecases` — the directory name is the one a reader sees when opening the tree.
package dev.valija.poc.application.usecases

import dev.valija.poc.application.ports.VaultReader
import dev.valija.poc.delivery.renderContextPackMarkdown
import dev.valija.poc.domain.VaultErrorCodes
import dev.valija.poc.domain.services.ContextPack
import dev.valija.poc.domain.values.SUPPORTED_SCHEMA_VERSION
import dev.valija.poc.domain.services.assembleContextPack
import dev.valija.poc.domain.vaultErr
import dev.valija.poc.shared.UseCase
import dev.valija.poc.shared.VaultResult
import dev.valija.poc.shared.err
import dev.valija.poc.shared.ok

data class ReadContextPackInput(
    val projectName: String,
    /** Passed through to the pack verbatim — the fixture's published `generatedAt` (D-7). */
    val generatedAt: String,
    /** `null` for the unbudgeted export path. */
    val budgetTokens: Int? = null,
)

data class RenderedPack(
    val pack: ContextPack,
    val markdown: String,
)

/**
 * Open a vault through the port, read one project, assemble and render its pack.
 *
 * It knows the port; it has never heard of SQLite. Refusing an unsupported `schema_version`
 * happens **here**, before anything is read — a reader never migrates and never renders a
 * partial view of a schema it does not understand (M4 D-J, `docs/vault-format.md` §11).
 */
class ReadContextPack(private val reader: VaultReader) :
    UseCase<ReadContextPackInput, RenderedPack> {

    override fun execute(input: ReadContextPackInput): VaultResult<RenderedPack> {
        val schemaVersion = reader.readSchemaVersion()
        if (schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            return err(
                vaultErr(
                    VaultErrorCodes.SCHEMA_TOO_NEW,
                    "Vault schema is $schemaVersion; this reader understands " +
                        "$SUPPORTED_SCHEMA_VERSION. Update the app — a reader never migrates.",
                ),
            )
        }

        val project = reader.findProjectByName(input.projectName)
            ?: return err(
                vaultErr(
                    VaultErrorCodes.PROJECT_NOT_FOUND,
                    "No project named '${input.projectName}' in this vault.",
                ),
            )

        val items = reader.findActiveItems(project.id)
        val pack = assembleContextPack(
            projectName = project.name,
            items = items,
            generatedAt = input.generatedAt,
            budgetTokens = input.budgetTokens,
        )
        return ok(RenderedPack(pack, renderContextPackMarkdown(pack)))
    }
}
