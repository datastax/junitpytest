# Copyright DataStax, Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import platform
import pluggy
import py
import py.io
import pytest
import six
import sys
import _pytest.python
from _pytest.main import EXIT_INTERRUPTED


def pytest_configure(config):
    if config.getoption("gradle"):
        config.pluginmanager.register(GradlePlugin(config), "gradle-plugin")


def pytest_addoption(parser):
    parser.addoption("--gradle", action="store_true", default=False,
                     help="Enables Gradle-JUnit-Jupiter test engine support for pytest")


# noinspection PyMethodMayBeStatic
class GradlePlugin(object):
    def __init__(self, config):
        self.config = config
        self._keyboardinterrupt_memo = None
        self._numcollected = 0
        self.reportchars = getreportopt(config)
        self._running_test = False
        self.stats = {}
        self.startdir = py.path.local()
        self._tw = self.capture_tw()
        self._logfragments = {}
        self.collect_only = config.getoption("--collect-only")
        self._outputs = set()

        # No output to the terminal, output stuff in a way that the JUnit-Pytest-Engine can parse
        config.pluginmanager.set_blocked("terminalreporter")

    def register_outputs(self, files_or_dirs):
        if not files_or_dirs:
            return
        if not isinstance(files_or_dirs, list) and not isinstance(files_or_dirs, set):
            files_or_dirs = [files_or_dirs]
        for file_or_dir in files_or_dirs:
            self._outputs.add(file_or_dir)

    def pytest_itemcollected(self, item):
        if self.collect_only:
            # <TestCaseFunction 'test_wrapped_access_calls_refresh'>
            # <UnitTestCase 'UpdatingKeyspaceMetadataWrapperTest'>
            # <Module 'meta_tests/utils_test/metadata_wrapper_test.py'>

            # <Function 'test_copy_from_with_wrong_order_or_missing_UDT_fields'>
            # <Instance '()'>
            # <Class 'TestCqlshCopy'>

            functionname = item.name
            rawname = item.originalname if item.originalname else item.name
            instance_or_class = item.parent
            clazz = instance_or_class.parent if not isinstance(instance_or_class, _pytest.python.Class) else instance_or_class
            classname = clazz.name
            module = clazz.parent
            print("{}::{}::{}::{}".format(module.name, classname, rawname, functionname))

    def pytest_internalerror(self, excrepr):
        self.to_junit("internalerror", dict(excrepr=six.text_type(excrepr)))
        return 1

    def pytest_keyboard_interrupt(self, excinfo):
        self._keyboardinterrupt_memo = excinfo.getrepr(funcargs=True)

    # noinspection PyUnusedLocal
    def pytest_exception_interact(self, node, call, report):
        excinfo_native = call.excinfo.getrepr(funcargs=True, tbfilter=False, showlocals=True, style="native")
        excinfo_long = call.excinfo.getrepr(funcargs=True, tbfilter=False, showlocals=True, style="long")
        if self._running_test:
            self._logfragments['excinfo_when'] = call.when
            # excinfo_native: ReprExceptionInfo
            # excinfo.reprtraceback: ReprTraceback
            # excinfo.reprcash: ReprFileLocation
            self._logfragments['excinfo_path'] = excinfo_native.reprcrash.path
            self._logfragments['excinfo_line_number'] = excinfo_native.reprcrash.lineno
            self._logfragments['excinfo_msg'] = excinfo_native.reprcrash.message
            self._logfragments['excinfo_traceback'] = self.capture_repr(excinfo_native.reprtraceback)
            self._logfragments['excinfo_long'] = excinfo_long
        else:
            self.to_junit("exception_interact", dict(
                node=node,
                call_when=call.when,  # one of "setup", "call", "teardown", "memocollect"
                excinfo_native=excinfo_native,
                excinfo_long=excinfo_long
            ))

    def pytest_runtest_logstart(self, nodeid, location):
        self.to_junit("runtest_logstart", dict(
            nodeid=nodeid,
            fspath=location[0],
            line_number=location[1],
            domain=location[2]
        ))
        self._running_test = True
        self._logfragments.clear()

    def pytest_runtest_logreport(self, report):
        rep = report
        res = self.config.hook.pytest_report_teststatus(report=rep)
        cat, letter, word = res
        if isinstance(word, tuple):
            word, _ = word
        if cat:
            self._logfragments['result_category'] = cat
        if word:
            self._logfragments['result_word'] = word

        self._logfragments['nodeid'] = rep.nodeid

        when = rep.when

        if len(self._outputs) > 0:
            self._logfragments['outputs'] = "\n".join(self._outputs)
            self._outputs.clear()

        if hasattr(rep, "location"):
            fspath, linenum, domain = rep.location
            self._logfragments['fspath'] = fspath
            self._logfragments['line_number'] = linenum
            self._logfragments['domain'] = domain

        if isinstance(rep.longrepr, tuple):
            self._logfragments['longrepr_fspath'] = rep.longrepr[0]
            self._logfragments['longrepr_line_number'] = rep.longrepr[1]
            self._logfragments['longrepr_msg'] = rep.longrepr[2]
        elif hasattr(rep.longrepr, 'toterminal'):
            self._logfragments['longrepr_msg'] = self.capture_repr(rep.longrepr)

        captured = self.capture_repr(rep)
        if len(captured) > 0:
            self._logfragments['buffered_{}'.format(when)] = captured

        for secname, content in rep.sections:
            self._logfragments[secname] = content

    # noinspection PyUnusedLocal
    def pytest_runtest_logfinish(self, nodeid):
        if self._running_test:
            self._running_test = False
            self.to_junit("runtest_logfinish", self._logfragments)
            self._logfragments.clear()

    # noinspection PyUnusedLocal
    def pytest_sessionstart(self, session):
        verinfo = platform.python_version()
        msg = "platform %s -- Python %s" % (sys.platform, verinfo)
        if hasattr(sys, "pypy_version_info"):
            verinfo = ".".join(map(str, sys.pypy_version_info[:3]))
            msg += "[pypy-%s-%s]" % (verinfo, sys.pypy_version_info[3])
        msg += ", pytest-%s, py-%s, pluggy-%s" % (
            pytest.__version__,
            py.__version__,
            pluggy.__version__,
        )

        self.to_junit("sessionstart", dict(
            platform=sys.platform,
            info=msg,
        ))

    @pytest.hookimpl(hookwrapper=True)
    def pytest_sessionfinish(self, exitstatus):
        outcome = yield
        outcome.get_result()

        info = {'exitstatus': exitstatus}

        if exitstatus == EXIT_INTERRUPTED:
            excrepr = self._keyboardinterrupt_memo
            del self._keyboardinterrupt_memo

            info['kbdintr_message'] = excrepr.reprcrash.message
            info['kbdintr_excinfo'] = self.capture_repr(excrepr)

        self.to_junit("sessionfinish", info)

    ##
    # Data to pytest-junit-engine
    ##

    def to_junit(self, message, strings):
        """
        Writes data to the pytest-junit-engine.

        The protocol is rather simple. A "START" message, followed by a couple of "Name: Value" pairs terminated
        by an empty line. The "Message" and "Blocks" headers are mandatory, other headers may exist.

            *** START\n
            Message: <message>\n
            Blocks: <number-of-data-blocks\n
            \n
            <data-blocks-see-below>
            *** END\n

        Each data-block:

            <number-of-bytes>\n
            <binary-data>\n
            \n

        Text encodinng is UTF-8
        """
        if self.collect_only:
            return

        if not isinstance(strings, dict):
            raise Exception("'strings' must be a dict")

        out = sys.stdout

        out.write("*** START/{}/{}\n".format(message, len(strings)))

        for k, v in strings.items():
            utf = str(v).encode("utf-8")
            out.write("{}: {}\n".format(k, len(utf)))
            out.flush()
            out.buffer.write(utf)
            out.write("\n")
        out.write("*** END\n")
        out.flush()

    # def pytest_logwarning(self, code, fslocation, message, nodeid):
    #     warnings = self.stats.setdefault("warnings", [])
    #     warning = WarningReport(
    #         code=code, fslocation=fslocation, message=message, nodeid=nodeid
    #     )
    #     warnings.append(warning)
    #
    # def _write_report_lines_from_hooks(self, lines):
    #     lines.reverse()
    #     for line in collapse(lines):
    #         self.write_line(line)
    #

    ##
    # Some methods called from the "outer world"
    ##

    # noinspection PyUnusedLocal
    def write_ensure_prefix(self, prefix, extra="", **kwargs):
        pass

    def ensure_newline(self):
        pass

    # noinspection PyUnusedLocal
    def write(self, content, **markup):
        pass

    # noinspection PyUnusedLocal
    def write_line(self, line, **markup):
        pass

    def rewrite(self, line, **markup):
        pass

    # noinspection PyUnusedLocal
    def write_sep(self, sep, title=None, **markup):
        pass

    # noinspection PyUnusedLocal
    def section(self, title, sep="=", **kw):
        pass

    def line(self, msg, **kw):
        pass

    def capture_repr(self, obj):
        tw = self.capture_tw()
        obj.toterminal(tw)
        return self.captured_tw(tw)

    def capture_tw(self):
        tw = py.io.TerminalWriter(stringio=True)
        setattr(tw, 'config', self.config)
        setattr(tw, 'reportchars', self.reportchars)
        tw.hasmarkup = False
        return tw

    def captured_tw(self, tw):
        exc = tw.stringio.getvalue()
        return exc.strip()


# class WarningReport(object):
#     """
#     Simple structure to hold warnings information captured by ``pytest_logwarning``.
#     """
#
#     def __init__(self, code, message, nodeid=None, fslocation=None):
#         """
#         :param code: unused
#         :param str message: user friendly message about the warning
#         :param str|None nodeid: node id that generated the warning (see ``get_location``).
#         :param tuple|py.path.local fslocation:
#             file system location of the source of the warning (see ``get_location``).
#         """
#         self.code = code
#         self.message = message
#         self.nodeid = nodeid
#         self.fslocation = fslocation
#
#     def get_location(self, config):
#         """
#         Returns the more user-friendly information about the location
#         of a warning, or None.
#         """
#         if self.nodeid:
#             return self.nodeid
#         if self.fslocation:
#             if isinstance(self.fslocation, tuple) and len(self.fslocation) >= 2:
#                 filename, linenum = self.fslocation[:2]
#                 relpath = py.path.local(filename).relto(config.invocation_dir)
#                 return "%s:%s" % (relpath, linenum)
#             else:
#                 return str(self.fslocation)
#         return None


def getreportopt(config):
    reportopts = ""
    reportchars = config.option.reportchars
    if not config.option.disable_warnings and "w" not in reportchars:
        reportchars += "w"
    elif config.option.disable_warnings and "w" in reportchars:
        reportchars = reportchars.replace("w", "")
    if reportchars:
        for char in reportchars:
            if char not in reportopts and char != "a":
                reportopts += char
            elif char == "a":
                reportopts = "fEsxXw"
    return reportopts
