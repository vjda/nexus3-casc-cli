import os

import typer


class Logger:
    log_levels = {
        'FATAL': [100, typer.colors.RED, True],
        'ERROR': [200, typer.colors.RED, True],
        'WARNING': [300, typer.colors.YELLOW, False],
        'INFO': [400, typer.colors.GREEN, False],
        'DEBUG': [500, typer.colors.MAGENTA, False]
    }

    @staticmethod
    def _log(level: str, message: str):
        config_level = os.getenv('NEXUS3_CASC_LOG_LEVEL', 'INFO')
        priority, color, stderr = Logger.log_levels[level]
        if priority <= Logger.log_levels[config_level][0]:
            message_colored = typer.style(message, fg=color)
            typer.echo(message_colored, err=stderr)

    @staticmethod
    def fatal(message: str):
        Logger._log('FATAL', message)

    @staticmethod
    def error(message: str):
        Logger._log('ERROR', message)

    @staticmethod
    def warning(message: str):
        Logger._log('WARNING', message)

    @staticmethod
    def info(message: str):
        Logger._log('INFO', message)

    @staticmethod
    def debug(message: str):
        Logger._log('DEBUG', message)
