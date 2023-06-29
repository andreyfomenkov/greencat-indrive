package ru.fomenkov.parser

import org.junit.Test
import kotlin.test.assertEquals

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
                "        project/FooBar.kt"

        val parser = GitStatusParser(output.split('\n'))

        assertEquals(
            setOf(
                "project/FooBarDecorationRenamed.kt",
                "project/FooBarFragment.kt",
                "project/FooBarInteractor.kt",
                "project/FooBar.kt",
            ),
            parser.parse(),
        )
    }
}