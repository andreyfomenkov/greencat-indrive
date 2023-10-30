package ru.fomenkov.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class GitStatusParserTest {

    @Test
    fun `Test parse git status output`() {
        val output = "On branch my-test-branch\n" +
                "Your branch is up to date with 'origin/my-test-branch'.\n" +
                "\n" +
                "Changes to be committed:\n" +
                "  (use \"git restore --staged <file>...\" to unstage)\n" +
                "        deleted:    project/FooBarDelegate.kt\n" +
                "        renamed:    project/FooBarDecoration.kt -> project/FooBarDecorationRenamed.kt\n" +
                "\n" +
                "Changes not staged for commit:\n" +
                "  (use \"git add <file>...\" to update what will be committed)\n" +
                "  (use \"git restore <file>...\" to discard changes in working directory)\n" +
                "        modified:   project/FooBarFragment.kt\n" +
                "        modified:   project/FooBarInteractor.kt\n" +
                "\n" +
                "Untracked files:\n" +
                "  (use \"git add <file>...\" to include in what will be committed)\n" +
                "        project/FooBar.kt\n" +
                "\n" +
                "\n" +
                "It took 2.12 seconds to enumerate untracked files. 'status -uno'\n" +
                "may speed it up, but you have to be careful not to forget to add\n" +
                "new files yourself (see 'git help status').\n" +
                "no changes added to commit (use \"git add\" and/or \"git commit -a\")"

        val parser = GitStatusParser(output.split('\n'))

        assertEquals(
            GitStatusParser.Output(
                branch = "my-test-branch",
                files = setOf(
                    "project/FooBarDecorationRenamed.kt",
                    "project/FooBarFragment.kt",
                    "project/FooBarInteractor.kt",
                    // File "project/FooBar.kt" was excluded, because it doesn't exist for test
                ),
            ),
            parser.parse(),
        )
    }
}