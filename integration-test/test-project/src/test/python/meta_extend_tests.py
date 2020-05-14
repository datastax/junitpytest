import pytest
from meta_tests import TestMeta


class TestExtend(TestMeta):
    def test_something_more(self):
        pass

    @pytest.mark.parametrize("param", ['a', 'b', 'c'])
    def test_param_extend(self, param):
        if param == 'b':
            raise Exception("param b fails")
        if param == 'c':
            pytest.skip("param c skips")
