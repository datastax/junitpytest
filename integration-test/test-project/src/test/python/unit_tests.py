from unittest import TestCase


class TestUnit(TestCase):
    def test_something(self):
        pass

    def test_fail(self):
        raise Exception("TestUnit.test_fail really fails")
