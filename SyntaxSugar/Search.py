from dataclasses import dataclass
import functools
import inspect
import re

@dataclass
class Game:
  name: ...
  price: ...
  reviews: ... = None # remember, mutable defaults!
  def __str__(self):
    return f"{self.name} (${self.price:.2f})"

games = [
  Game("Factorio", 35),
  Game("Counter-Strike", 0),
  Game("Tetris", float("inf")),
  Game("Roller Coaster Tycoon 3", 19.99),
  Game(name="Portal 2", price=9.99, reviews=[
    {"rating": 1, "message": "Still no cake?!? (╯°□°）╯︵ ┻━┻"},
  ]),
  Game(name="Civilization VII", price=99.99, reviews=[
    {"rating": 3, "message": "Overpriced, y'know?"},
    {"rating": 5, "message": "Greatest thing since Civ 6."},
    {"rating": 2, "message": "Worst thing since Civ 5."},
  ]),
  Game(name="Stardew Valley", price=14.99, reviews=[
    {"rating": 5, "message": "It's okay, I guess. (835 hours on record)."},
  ]),
]

# Part 2: Decorators
# Note: Defined first to allow use in query functions.

def contract(condition):
  # this returns a decorator that checks a condition.
  def decorator(function):
    @functools.wraps(function)
    def wrapper(*args, **kwargs):
      # introspects the function call to get argument values.
      signature = inspect.signature(function)
      bound_arguments = signature.bind(*args, **kwargs)
      bound_arguments.apply_defaults()
      
      # prepares the environment for condition evaluation.
      context = bound_arguments.arguments
      global_namespace = function.__globals__
      
      # validates the condition. Returns False if it fails.
      condition_met = eval(condition, global_namespace, context)
      if not condition_met:
        return False
        
      # executes the original function if the contract is satisfied.
      return function(*args, **kwargs)
      
    return wrapper
  return decorator

# Part 1: Comprehensions
# IMPORTANT: Do NOT use loops/map/filter/etc!
# Part 3: Validation via @contract

def queryAll():
  return [str(g) for g in games] # returns all games as strings using comprehension.

@contract("len(regex) > 0") # Part 3 validation.
def queryByName(regex):
  # find games where the name matches the regex pattern.
  matching_games = [
    game 
    for game in games 
    if re.search(regex, game.name)
  ]
  # convert the surviving games into formatted display strings.
  display_strings = [str(game) for game in matching_games]
  return display_strings

@contract("0 <= min <= max") # Part 3 validation.
def queryByPrice(min = 0, max = float("inf")):
  # keep games that are below the maximum price.
  not_too_expensive = [game for game in games if game.price <= max]
  # from those, filter for games above the minimum price.
  within_price_range = [game for game in not_too_expensive if game.price >= min]
  # turn the final selection of games into display strings.
  results = [str(game) for game in within_price_range]
  return results

@contract("0 <= min <= 5") # Part 3 validation.
def queryByRating(min):
  # Reminder: Do NOT use a rating helper function (nest comprehensions)
  # pre-calculates the average score for every game available.
  all_scores = [
    sum(review['rating'] for review in game.reviews) / len(game.reviews) if game.reviews else 0
    for game in games
  ]
  # groups each game with its corresponding average score.
  games_with_scores = zip(games, all_scores)
  # filters for games whose score meets or exceeds the minimum.
  qualified_games = [
    game 
    for game, score in games_with_scores 
    if score >= min
  ]
  # return names and prices as a list of strings.
  return [str(game) for game in qualified_games]

@contract("nameRegex is None or len(nameRegex) > 0") # Part 3 validation.
@contract("0 <= priceMin <= priceMax") # Part 3 validation.
@contract("0 <= ratingMin <= 5") # Part 3 validation.
def queryBy(nameRegex = None, priceMin = 0, priceMax = float("inf"), ratingMin = 0):
  # filters games by name using the regex first.
  name_matches = [g for g in games if not nameRegex or re.search(nameRegex, g.name) ]
  # narrows down the results by checking the price range.
  price_matches = [g for g in name_matches if priceMin <= g.price <= priceMax]
  # finally, calculates average ratings and filters for highly rated games.
  rating_matches = [
    game 
    for game in price_matches 
    if (sum(r['rating'] for r in game.reviews) / len(game.reviews) if game.reviews else 0) >= ratingMin
  ]
  # transforms the list of qualified games into a list of display strings.
  return [str(game) for game in rating_matches]

# Provided Tests:

if __name__ == "__main__":
  def test(name, test, expected):
    try:
      received = test()
      if received == expected:
          print(f" - {name}: passed")
      else:
          print(f" - {name}: \n    - expected {expected}, \n    - received {received}")
    except Exception as e:
        print(f" - {name}: \n    - expected {expected}, \n    - received Exception {e}")

  print("Part 1:")
  test("All", lambda: queryAll(), [
    "Factorio ($35.00)",
    "Counter-Strike ($0.00)",
    "Tetris ($inf)",
    "Roller Coaster Tycoon 3 ($19.99)",
    "Portal 2 ($9.99)",
    "Civilization VII ($99.99)",
    "Stardew Valley ($14.99)",
  ])
  test("Name", lambda: queryByName("Stardew Valley"), [
    "Stardew Valley ($14.99)",
  ])
  test("Name Regex", lambda: queryByName("\\d+"), [
    "Roller Coaster Tycoon 3 ($19.99)",
    "Portal 2 ($9.99)",
  ])
  test("Price Min", lambda: queryByPrice(min=35.00), [
    "Factorio ($35.00)",
    "Tetris ($inf)",
    "Civilization VII ($99.99)",
  ])
  test("Price Max", lambda: queryByPrice(max=15.00), [
    "Counter-Strike ($0.00)",
    "Portal 2 ($9.99)",
    "Stardew Valley ($14.99)",
  ])
  test("Rating 0", lambda: queryByRating(0), [
    "Factorio ($35.00)",
    "Counter-Strike ($0.00)",
    "Tetris ($inf)",
    "Roller Coaster Tycoon 3 ($19.99)",
    "Portal 2 ($9.99)",
    "Civilization VII ($99.99)",
    "Stardew Valley ($14.99)",
  ])
  test("Rating 1", lambda: queryByRating(1), [
    "Portal 2 ($9.99)",
    "Civilization VII ($99.99)",
    "Stardew Valley ($14.99)",
  ])
  test("By Name, Price Max", lambda: queryBy(nameRegex="C", priceMax=25.00), [
    "Counter-Strike ($0.00)",
    "Roller Coaster Tycoon 3 ($19.99)",
  ])

  print("\nPart 2:")
  
  @contract("0 < 1")
  def success():
    return True;
  test("Success", lambda: success(), True)
  
  @contract("0 > 1")
  def failure():
    return True;
  test("Failure", lambda: failure(), False)
  
  @contract("n >= 0")
  def fib(n):
    return True;
  test("fib(10)", lambda: fib(10), True)
  test("fib(-10)", lambda: fib(-10), False)

  @contract("key is not None")
  @contract("'required' in options")
  def resolve(key="string", **options):
    return True;
  test("Default Key", lambda: resolve(required=True), True)
  test("Missing Required", lambda: resolve(key="key"), False)

  @contract("len(games) > 0")
  def bonus():
    return True;
  test("Bonus Globals", lambda: bonus(), True)
  # Untested: Nonlocals...

  print("\nPart 3:")

  test("Name Regex Empty", lambda: queryByName(""), False)
  test("Price Min Negative", lambda: queryByPrice(min=-1), False)
