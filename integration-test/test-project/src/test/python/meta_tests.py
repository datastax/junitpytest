import pytest
import sys


class TestMeta:

    def test_meta_1(self):
        sys.stdout.write("foo bar stdout\nstdout more")
        sys.stderr.write("BAZ HOOP STDERR\nsMORE STDERR")
        raise Exception("balh")

    def test_fail2(self):
        self.nested()

    def nested(self):
        raise Exception("fail as well")

    def test_ok(self):
        pass

    def test_skip(self):
        pytest.skip("skip because it's cool to ignore stuff")

    @pytest.mark.parametrize("param", ['a', 'b', 'c'])
    def test_parameterized(self, param):
        if param == 'b':
            raise Exception("param b fails")
        if param == 'c':
            pytest.skip("param c skips")
