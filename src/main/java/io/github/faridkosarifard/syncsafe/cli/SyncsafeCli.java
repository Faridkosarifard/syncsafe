package io.github.faridkosarifard.syncsafe.cli;

import io.github.faridkosarifard.syncsafe.SyncsafeVersion;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IExitCodeExceptionMapper;

/**
 * Entry point for the Syncsafe command-line interface.
 *
 * <p>Exit codes: 0 aligned, 2 usage error, 3 diverged, 4 incomplete.
 */
@Command(
    name = "syncsafe",
    mixinStandardHelpOptions = true,
    version = "syncsafe " + SyncsafeVersion.VERSION,
    description =
        "Delete-correctness verification across lakehouse table formats after metadata"
            + " conversion.",
    subcommands = {
      VerifyCommand.class,
      ReportCommand.class,
      DemoCommand.class,
      CommandLine.HelpCommand.class
    })
public final class SyncsafeCli {
  public static void main(String[] args) {
    System.exit(run(args));
  }

  /** In-process entry used by tests; does not call {@link System#exit}. */
  public static int run(String[] args) {
    CommandLine commandLine = new CommandLine(new SyncsafeCli());
    commandLine.getCommandSpec().exitCodeOnUsageHelp(0);
    commandLine.getCommandSpec().exitCodeOnVersionHelp(0);
    commandLine.getCommandSpec().exitCodeOnInvalidInput(ExitCodes.USAGE);
    commandLine.getCommandSpec().exitCodeOnExecutionException(ExitCodes.INCOMPLETE);
    commandLine.setExecutionExceptionHandler(new QuietExecutionExceptionHandler());
    commandLine.setExitCodeExceptionMapper(new UsageMapper());
    commandLine.setParameterExceptionHandler(new UsageParameterHandler());
    return commandLine.execute(args);
  }

  static final class UsageMapper implements IExitCodeExceptionMapper {
    @Override
    public int getExitCode(Throwable exception) {
      if (exception instanceof IllegalArgumentException
          || exception instanceof CommandLine.ParameterException) {
        return ExitCodes.USAGE;
      }
      return ExitCodes.INCOMPLETE;
    }
  }

  static final class UsageParameterHandler implements CommandLine.IParameterExceptionHandler {
    @Override
    public int handleParseException(CommandLine.ParameterException ex, String[] args) {
      CommandLine cmd = ex.getCommandLine();
      if (cmd.isUsageHelpRequested()) {
        cmd.usage(cmd.getOut());
        return 0;
      }
      if (cmd.isVersionHelpRequested()) {
        cmd.printVersionHelp(cmd.getOut());
        return 0;
      }
      cmd.getErr().println(ex.getMessage());
      cmd.usage(cmd.getErr());
      return ExitCodes.USAGE;
    }
  }

  static final class QuietExecutionExceptionHandler
      implements CommandLine.IExecutionExceptionHandler {
    @Override
    public int handleExecutionException(
        Exception ex, CommandLine commandLine, CommandLine.ParseResult parseResult) {
      commandLine.getErr().println("Error: " + ex.getMessage());
      if (ex instanceof IllegalArgumentException) {
        return ExitCodes.USAGE;
      }
      return ExitCodes.INCOMPLETE;
    }
  }
}
