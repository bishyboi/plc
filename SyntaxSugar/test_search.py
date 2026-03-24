from Search import Game, games, queryAll, queryByName, queryByPrice, queryByRating, queryBy, contract
import unittest
import re

class TestSearchExtensive(unittest.TestCase):

    def test_queryAll(self):
        results = queryAll()
        self.assertEqual(len(results), len(games))
        self.assertTrue(all(isinstance(r, str) for r in results))
        self.assertIn("Factorio ($35.00)", results)

    def test_queryByName(self):
        # Exact match
        self.assertEqual(len(queryByName("Factorio")), 1)
        # Regex match
        self.assertEqual(len(queryByName(r"\d+")), 2) # RCT3 and Portal 2
        # Special Regex: '.' matches everything except newline
        self.assertEqual(len(queryByName(".")), 7)
        # Special Regex: Anchors
        self.assertEqual(len(queryByName("^Factorio$")), 1)
        self.assertEqual(len(queryByName("^Factorio")), 1)
        self.assertEqual(len(queryByName("Factorio$")), 1)
        # No match
        self.assertEqual(len(queryByName("Halo")), 0)
        # Case sensitivity (re.search is case sensitive by default)
        self.assertEqual(len(queryByName("factorio")), 0)
        # Empty string (should return False due to contract)
        self.assertFalse(queryByName(""))

    def test_queryByPrice(self):
        # Min price
        self.assertEqual(len(queryByPrice(min=35)), 3) # Factorio (35), Tetris (inf), Civ VII (99.99)
        # Max price
        self.assertEqual(len(queryByPrice(max=10)), 2) # CS (0), Portal 2 (9.99)
        # Boundary: Equality
        self.assertIn("Factorio ($35.00)", queryByPrice(min=35, max=35))
        # Boundary: Infinity
        self.assertEqual(len(queryByPrice(min=float("inf"))), 1) # Tetris
        self.assertEqual(len(queryByPrice(max=float("inf"))), 7)
        # Range
        self.assertEqual(len(queryByPrice(min=10, max=20)), 2) # RCT3 (19.99), Stardew (14.99)
        # Negative min (should return False due to contract)
        self.assertFalse(queryByPrice(min=-1))
        # Min > Max (should return False due to contract)
        self.assertFalse(queryByPrice(min=20, max=10))

    def test_queryByRating(self):
        # Rating 0 (all games, including those with no reviews)
        self.assertEqual(len(queryByRating(0)), 7)
        # Rating 5 (Stardew has [5], Civ VII has [3, 5, 2] -> 3.33)
        self.assertEqual(len(queryByRating(5)), 1) # Stardew
        # Rating 3 (Civ VII and Stardew)
        self.assertEqual(len(queryByRating(3)), 2)
        # Rating 1 (Portal 2 has [1], plus those above)
        self.assertEqual(len(queryByRating(1)), 3)
        # Exact Boundary for average (Stardew is exactly 5.0)
        self.assertEqual(len(queryByRating(5.0)), 1)
        # Invalid rating (should return False due to contract)
        self.assertFalse(queryByRating(-0.1))
        self.assertFalse(queryByRating(5.1))

    def test_queryBy(self):
        # Combined name and price
        # 'C' matches Counter-Strike ($0) and Civilization VII ($99.99) and Roller Coaster Tycoon 3
        self.assertEqual(len(queryBy(nameRegex="C", priceMax=25)), 2) # CS (0), RCT3 (19.99)
        # Full filter
        # Price 10-100: RCT3 (19.99), Portal 2 (9.99 - NO), Civ VII (99.99), Stardew (14.99), Factorio (35)
        # Of those, Rating >= 3: Civ VII (3.33), Stardew (5)
        self.assertEqual(len(queryBy(priceMin=10, priceMax=100, ratingMin=3)), 2)
        # All filters active
        self.assertEqual(len(queryBy(nameRegex="Valley", priceMin=10, priceMax=20, ratingMin=4)), 1)
        # Invalid contract
        self.assertFalse(queryBy(priceMin=-1))
        self.assertFalse(queryBy(ratingMin=6))

    def test_contract_decorator_isolated(self):
        # Basic comparison
        @contract("x > 0")
        def pos(x): return x
        self.assertEqual(pos(5), 5)
        self.assertFalse(pos(-5))

        # Logical operators in contract
        @contract("x > 0 and x < 10")
        def range_check(x): return x
        self.assertEqual(range_check(5), 5)
        self.assertFalse(range_check(11))
        self.assertFalse(range_check(-1))

        # String methods
        @contract("s.startswith('A')")
        def starts_with_a(s): return s
        self.assertEqual(starts_with_a("Apple"), "Apple")
        self.assertFalse(starts_with_a("Banana"))

        # Accessing globals from contract
        @contract("len(games) == 7")
        def check_global(): return True
        self.assertTrue(check_global())

        # Test defaults
        @contract("x == 10")
        def check_default(x=10): return x
        self.assertEqual(check_default(), 10)
        self.assertFalse(check_default(5))

if __name__ == "__main__":
    unittest.main()
