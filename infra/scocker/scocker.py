import json
import math
import random
import threading
import time

import sys
import logging

logger = logging.getLogger()
logger.setLevel(logging.DEBUG)

handler = logging.StreamHandler(sys.stdout)
handler.setFormatter(logging.Formatter(
    "%(asctime)s - %(levelname)s - %(message)s"
))

logger.handlers.clear()
logger.addHandler(handler)

from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import urlparse, parse_qs
from datetime import datetime, timedelta

stop = False
active = True

HOST = "0.0.0.0"
PORT = 8085

WORLD_CUP_TOURNAMENT_ID = "107"
current = datetime.now()
checkpoint = None
delta = 15

MATCHES_LOCK = threading.Lock()
MATCHES = [
    {"code": "1", "home": "MEX", "away": "RSA", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.45, "tie_odds": 4.07, "away_odds": 5.75, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-11 19:00:00", "%Y-%m-%d %H:%M:%S")},
    {"code": "2", "home": "KOR", "away": "CZE", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 2.57, "tie_odds": 3.24, "away_odds": 2.57, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-12 02:00:00", "%Y-%m-%d %H:%M:%S")},
    {"code": "3", "home": "CAN", "away": "BIH", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 2.00, "tie_odds": 3.63, "away_odds": 3.09, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-12 19:00:00", "%Y-%m-%d %H:%M:%S")},
    {"code": "4", "home": "USA", "away": "PAR", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.86, "tie_odds": 3.47, "away_odds": 3.80, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-13 01:00:00", "%Y-%m-%d %H:%M:%S")},
    {"code": "7", "home": "BRA", "away": "MAR", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.51, "tie_odds": 3.89, "away_odds": 5.50, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-13 22:00:00", "%Y-%m-%d %H:%M:%S")},
    {"code": "8", "home": "QAT", "away": "SUI", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 7.94, "tie_odds": 5.01, "away_odds": 1.29, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-13 22:00:00", "%Y-%m-%d %H:%M:%S")},
    {"code": "5", "home": "HAI", "away": "SCO", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 6.31, "tie_odds": 4.79, "away_odds": 1.38, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-14 01:00:00", "%Y-%m-%d %H:%M:%S")},
    {"code": "6", "home": "AUS", "away": "TUR", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 4.47, "tie_odds": 3.72, "away_odds": 1.66, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-14 04:00:00", "%Y-%m-%d %H:%M:%S")},
    {"code": "9", "home": "GER", "away": "CUW", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.02, "tie_odds": 21.0, "away_odds": 51.0, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-14 17:00:00", "%Y-%m-%d %H:%M:%S")},
    {"code": "11", "home": "NED", "away": "JPN", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.91, "tie_odds": 3.63, "away_odds": 3.47, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-14 20:00:00", "%Y-%m-%d %H:%M:%S")},
    {"code": "10", "home": "CIV", "away": "ECU", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 3.09, "tie_odds": 3.24, "away_odds": 2.14, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-14 23:00:00", "%Y-%m-%d %H:%M:%S")},
    {"code": "12", "home": "SWE", "away": "TUN", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.82, "tie_odds": 3.47, "away_odds": 3.80, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-15 02:00:00", "%Y-%m-%d %H:%M:%S")},
    {"code": "14", "home": "ESP", "away": "CPV", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.05, "tie_odds": 17.0, "away_odds": 34.0, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-15 16:00:00", "%Y-%m-%d %H:%M:%S")},
    {"code": "16", "home": "BEL", "away": "EGY", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.51, "tie_odds": 3.98, "away_odds": 5.50, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-15 19:00:00", "%Y-%m-%d %H:%M:%S")},
    {"code": "13", "home": "KSA", "away": "URU", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 5.01, "tie_odds": 3.80, "away_odds": 1.58, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-15 22:00:00", "%Y-%m-%d %H:%M:%S")},
    {"code": "15", "home": "IRN", "away": "NZL", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.66, "tie_odds": 3.63, "away_odds": 4.47, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-16 01:00:00", "%Y-%m-%d %H:%M:%S")},
    {"code": "17", "home": "FRA", "away": "SEN", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.41, "tie_odds": 4.47, "away_odds": 6.31, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-16 09:00:00", "%Y-%m-%d %H:%M:%S")},
    {"code": "18", "home": "IRQ", "away": "NOR", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 7.08, "tie_odds": 5.01, "away_odds": 1.29, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-16 22:00:00", "%Y-%m-%d %H:%M:%S")},
    {"code": "19", "home": "ARG", "away": "ALG", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.38, "tie_odds": 4.47, "away_odds": 7.08, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-17 01:00:00", "%Y-%m-%d %H:%M:%S")},
    {"code": "20", "home": "AUT", "away": "JOR", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.32, "tie_odds": 4.79, "away_odds": 8.51, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-17 04:00:00", "%Y-%m-%d %H:%M:%S")},
    {"code": "23", "home": "POR", "away": "COD", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.29, "tie_odds": 4.79, "away_odds": 8.91, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-17 17:00:00", "%Y-%m-%d %H:%M:%S")},
    {"code": "22", "home": "ENG", "away": "CRO", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.62, "tie_odds": 3.89, "away_odds": 4.47, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-17 20:00:00", "%Y-%m-%d %H:%M:%S")},
    {"code": "21", "home": "GHA", "away": "PAN", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.91, "tie_odds": 3.39, "away_odds": 3.47, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-17 23:00:00", "%Y-%m-%d %H:%M:%S")},
    {"code": "24", "home": "UZB", "away": "COL", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 7.08, "tie_odds": 4.47, "away_odds": 1.35, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-18 02:00:00", "%Y-%m-%d %H:%M:%S")},
    {"code": "25", "home": "CZE", "away": "RSA", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-18 16:00:00", "%Y-%m-%d %H:%M:%S")},
    {"code": "26", "home": "SUI", "away": "BIH", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-18 19:00:00", "%Y-%m-%d %H:%M:%S")},
    {"code": "27", "home": "CAN", "away": "QAT", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-18 22:00:00", "%Y-%m-%d %H:%M:%S")},
    {"code": "28", "home": "MEX", "away": "KOR", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-19 01:00:00", "%Y-%m-%d %H:%M:%S")},
    {"code": "32", "home": "USA", "away": "AUS", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-19 19:00:00", "%Y-%m-%d %H:%M:%S")},
    {"code": "30", "home": "SCO", "away": "MAR", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-19 22:00:00", "%Y-%m-%d %H:%M:%S")},
    {"code": "29", "home": "BRA", "away": "HAI", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-20 01:00:00", "%Y-%m-%d %H:%M:%S")},
    {"code": "31", "home": "TUR", "away": "PAR", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-20 04:00:00", "%Y-%m-%d %H:%M:%S")},
    {"code": "35", "home": "NED", "away": "SWE", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-20 17:00:00", "%Y-%m-%d %H:%M:%S")},
    {"code": "33", "home": "GER", "away": "CIV", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-20 20:00:00", "%Y-%m-%d %H:%M:%S")},
    {"code": "34", "home": "ECU", "away": "CUW", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-21 00:00:00", "%Y-%m-%d %H:%M:%S")},
    {"code": "36", "home": "TUN", "away": "JPN", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-21 04:00:00", "%Y-%m-%d %H:%M:%S")},
    {"code": "38", "home": "ESP", "away": "KSA", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-21 16:00:00", "%Y-%m-%d %H:%M:%S")},
    {"code": "39", "home": "BEL", "away": "IRN", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-21 19:00:00", "%Y-%m-%d %H:%M:%S")},
    {"code": "37", "home": "URU", "away": "CPV", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-21 22:00:00", "%Y-%m-%d %H:%M:%S")},
    {"code": "40", "home": "NZL", "away": "EGY", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-22 01:00:00", "%Y-%m-%d %H:%M:%S")},
    {"code": "43", "home": "ARG", "away": "AUT", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-22 17:00:00", "%Y-%m-%d %H:%M:%S")},
    {"code": "42", "home": "FRA", "away": "IRQ", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-22 21:00:00", "%Y-%m-%d %H:%M:%S")},
    {"code": "41", "home": "NOR", "away": "SEN", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-23 00:00:00", "%Y-%m-%d %H:%M:%S")},
    {"code": "44", "home": "JOR", "away": "ALG", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-23 02:00:00", "%Y-%m-%d %H:%M:%S")},
    {"code": "47", "home": "POR", "away": "UZB", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-23 17:00:00", "%Y-%m-%d %H:%M:%S")},
    {"code": "45", "home": "ENG", "away": "GHA", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-23 20:00:00", "%Y-%m-%d %H:%M:%S")},
    {"code": "46", "home": "PAN", "away": "CRO", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-23 23:00:00", "%Y-%m-%d %H:%M:%S")},
    {"code": "48", "home": "COL", "away": "COD", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-24 02:00:00", "%Y-%m-%d %H:%M:%S")},
    {"code": "52", "home": "BIH", "away": "QAT", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-24 19:00:00", "%Y-%m-%d %H:%M:%S")},
    {"code": "51", "home": "SUI", "away": "CAN", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-24 19:00:00", "%Y-%m-%d %H:%M:%S")},
    {"code": "50", "home": "MAR", "away": "HAI", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-24 22:00:00", "%Y-%m-%d %H:%M:%S")},
    {"code": "49", "home": "SCO", "away": "BRA", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-24 22:00:00", "%Y-%m-%d %H:%M:%S")},
    {"code": "54", "home": "RSA", "away": "KOR", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-25 01:00:00", "%Y-%m-%d %H:%M:%S")},
    {"code": "53", "home": "CZE", "away": "MEX", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-25 01:00:00", "%Y-%m-%d %H:%M:%S")},
    {"code": "55", "home": "CUW", "away": "CIV", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-25 20:00:00", "%Y-%m-%d %H:%M:%S")},
    {"code": "56", "home": "ECU", "away": "GER", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-25 20:00:00", "%Y-%m-%d %H:%M:%S")},
    {"code": "57", "home": "JPN", "away": "SWE", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-25 23:00:00", "%Y-%m-%d %H:%M:%S")},
    {"code": "58", "home": "TUN", "away": "NED", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-25 23:00:00", "%Y-%m-%d %H:%M:%S")},
    {"code": "59", "home": "TUR", "away": "USA", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-26 02:00:00", "%Y-%m-%d %H:%M:%S")},
    {"code": "60", "home": "PAR", "away": "AUS", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-26 02:00:00", "%Y-%m-%d %H:%M:%S")},
    {"code": "62", "home": "SEN", "away": "IRQ", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-26 19:00:00", "%Y-%m-%d %H:%M:%S")},
    {"code": "61", "home": "NOR", "away": "FRA", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-26 19:00:00", "%Y-%m-%d %H:%M:%S")},
    {"code": "65", "home": "CPV", "away": "KSA", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-27 00:00:00", "%Y-%m-%d %H:%M:%S")},
    {"code": "66", "home": "URU", "away": "ESP", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-27 00:00:00", "%Y-%m-%d %H:%M:%S")},
    {"code": "63", "home": "EGY", "away": "IRN", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-27 03:00:00", "%Y-%m-%d %H:%M:%S")},
    {"code": "64", "home": "NZL", "away": "BEL", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-27 03:00:00", "%Y-%m-%d %H:%M:%S")},
    {"code": "67", "home": "PAN", "away": "ENG", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-27 21:00:00", "%Y-%m-%d %H:%M:%S")},
    {"code": "68", "home": "CRO", "away": "GHA", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-27 21:00:00", "%Y-%m-%d %H:%M:%S")},
    {"code": "71", "home": "COL", "away": "POR", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-27 23:30:00", "%Y-%m-%d %H:%M:%S")},
    {"code": "72", "home": "COD", "away": "UZB", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-27 23:30:00", "%Y-%m-%d %H:%M:%S")},
    {"code": "69", "home": "JOR", "away": "ARG", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-28 02:00:00", "%Y-%m-%d %H:%M:%S")},
    {"code": "70", "home": "ALG", "away": "AUT", "minutes": 0, "half": 0, "first_half_added_time": None, "second_half_added_time": None, "home_goals": 0, "away_goals": 0, "home_odds": 1.00, "tie_odds": 1.00, "away_odds": 1.00, "odds_calculated_at": None, "status": "TO_START", "odds_changed_at": current, "started_at": datetime.strptime("2026-06-28 02:00:00", "%Y-%m-%d %H:%M:%S")},
]

def odds_to_probabilities(odd_a, odd_draw, odd_b):
    inv_a = 1 / odd_a
    inv_d = 1 / odd_draw
    inv_b = 1 / odd_b
    total = inv_a + inv_d + inv_b
    return inv_a / total, inv_d / total, inv_b / total

def poisson(lmbda):
    L = math.exp(-lmbda)
    k = 0
    p = 1
    while p > L:
        k += 1
        p *= random.random()
    return k - 1

def simulate_score(minute, match):
    # calculating match probs
    inv_a = 1 / match["home_odds"]
    inv_d = 1 / match["tie_odds"]
    inv_b = 1 / match["away_odds"]
    total = inv_a + inv_d + inv_b
    prob_home, prob_draw, prob_away = inv_a / total, inv_d / total, inv_b / total

    # setting total expected goals
    progress = minute / 90
    average_goals = 2.7
    imbalance = abs(prob_home - prob_away)
    goal_inflation = 1 + 1.5 * imbalance # for huge imbalance, more goals
    expected_total_goals = average_goals * goal_inflation * progress

    # set each team goal share
    strength_a = prob_home + 0.5 * prob_draw
    strength_b = prob_away + 0.5 * prob_draw
    share_a = strength_a / (strength_a + strength_b)
    share_b = strength_b / (strength_a + strength_b)
    lambda_a = expected_total_goals * share_a
    lambda_b = expected_total_goals * share_b

    # calculating goals
    goals_a = poisson(lambda_a)
    goals_b = poisson(lambda_b)
    return goals_a, goals_b


def simulate_added(half):
    if half == 1:
        mean = 2.5
        std = 1.0
    else:
        mean = 4.5
        std = 1.8

    value = random.gauss(mean, std)
    value = max(0, min(value, 12))
    return round(value)


def diff_minutes(date1, date2):
    return (date1 - date2).total_seconds() / 60

def diff_hours(date1, date2):
    return diff_minutes(date1, date2) / 60

def or_zero(number):
    return number if number is not None else 0


def simulate_matches():
    while not stop:
        time.sleep(1)
        if active:

            global checkpoint, current, delta
            current = current + timedelta(minutes=delta)
            if not checkpoint or current.date() != checkpoint:
                logger.debug(f"Simulating @ {current.date()}")
                checkpoint = current.date()

            for match in MATCHES:
                if match["status"] == "COMPLETED":
                    continue

                match_ended = False
                if current > match["started_at"]:
                    new_status = "IN_PLAY"
                    minutes = diff_minutes(current, match["started_at"])

                    new_half, new_minutes, new_added_time1, new_added_time2 = None, None, None, None
                    if minutes < 45:
                        new_half = 1
                        new_minutes = minutes
                    elif minutes < 60:
                        new_half = 1
                        added_time = simulate_added(half=1)
                        new_added_time1 = added_time
                        new_minutes = minutes + added_time
                    else:
                        new_added_time1 = match["first_half_added_time"]
                        if not new_added_time1:
                            new_added_time1 = simulate_added(half=1)
                        if minutes < 110 + new_added_time1:
                            new_half = 2
                            new_minutes = minutes - (new_added_time1 + 20)
                        else:
                            new_half = 2
                            added_time = simulate_added(half=2)
                            new_added_time2 = added_time
                            new_minutes = 90 + added_time
                            match_ended = True
                            new_status = "COMPLETED"

                    home_goals, away_goals = simulate_score(new_minutes + or_zero(new_added_time1) + or_zero(new_added_time2), match)
                    new_home_goals = max(match["home_goals"], home_goals)
                    new_away_goals = max(match["away_goals"], away_goals)

                    if match_ended:
                        logger.info(f'Match ended: {match["home"]} {new_home_goals}-{match["away_goals"]} {new_away_goals}')

                    MATCHES_LOCK.acquire()
                    match["status"] = new_status
                    match["home_goals"] = new_home_goals
                    match["away_goals"] = new_away_goals
                    match["half"] = new_half
                    match["minutes"] = new_minutes
                    match["first_half_added_time"] = new_added_time1
                    match["second_half_added_time"] = new_added_time2
                    MATCHES_LOCK.release()

                    if match["started_at"] > current:
                        break

                else:
                    # Not yet played
                    if not match["odds_calculated_at"] or diff_hours(current, match["odds_calculated_at"]) >= 24:
                        match["home_odds"] = round(match["home_odds"] + random.choice([-1, 0, 1]) * match["home_odds"] * 0.02, 2)
                        match["tie_odds"] = round(match["tie_odds"] + random.choice([-1, 0, 1]) * match["home_odds"] * 0.02, 2)
                        match["away_odds"] = round(match["away_odds"] + random.choice([-1, 0, 1]) * match["home_odds"] * 0.02, 2)
                        match["odds_calculated_at"] = current


class ResultsMockerHandler(BaseHTTPRequestHandler):

    @staticmethod
    def _accept_pause():
        global active
        if active:
            logger.info("Pause received")
        else:
            logger.info("Resume received")
        active = not active
        return 202

    @staticmethod
    def _accept_stop():
        global stop
        logger.info("Stop received")
        stop = not stop
        return 202


    def _accept_current(self):
        content_length = int(self.headers.get("Content-Length", 0))
        body_bytes = self.rfile.read(content_length)
        body = json.loads(body_bytes.decode())
        new_time = body.get("time")
        if not new_time:
            return 400, {"error": "'time' is required"}
        try:
            global current
            current = datetime.fromisoformat(new_time)
            logger.info(f"Current changed to {new_time}")
            for match in MATCHES:
                match["odds_changed_at"] = current
            return 202, None
        except ValueError:
            return 400, {"error": "Invalid datetime format"}


    def _accept_delta(self):
        content_length = int(self.headers.get("Content-Length", 0))
        body_bytes = self.rfile.read(content_length)
        body = json.loads(body_bytes.decode())
        new_delta = body.get("delta")
        if not new_delta:
            return 400, {"error": "'delta' is required"}
        try:
            global delta
            delta = int(new_delta)
            logger.info(f"Delta changed to {new_delta} minutes")
            return 202, None
        except ValueError:
            return 400, {"error": "Invalid number format"}


    def _process_matches_request(self):
        parsed_url = urlparse(self.path)
        params = parse_qs(parsed_url.query)

        tournament_id = params.get("tournament_id", [None])[0]
        if tournament_id != WORLD_CUP_TOURNAMENT_ID:
            return 404, {"error": "Tournament not found"}
        return 200, MATCHES


    def _process_new_matches(self):
        content_length = int(self.headers.get("Content-Length", 0))
        body_bytes = self.rfile.read(content_length)
        body = json.loads(body_bytes.decode())
        new_matches = body.get("matches")
        if not new_matches:
            return 400, {"error": "'matches' is required"}
        try:
            global MATCHES
            MATCHES += new_matches
            logger.info(f"New matches added: {len(new_matches)}")
            return 201, None
        except ValueError:
            return 400, {"error": "Invalid number format"}


    def _send_json(self, code, payload=None):
        self.send_response(code)
        if payload is not None:
            self.send_header("Content-Type", "application/json")
        self.end_headers()
        if payload is not None:
            MATCHES_LOCK.acquire()
            self.wfile.write(json.dumps(payload, default=str).encode())
            MATCHES_LOCK.release()

    def do_GET(self):
        if urlparse(self.path).path == "/api-client/matches/live.json":
            status_code, response = self._process_matches_request()
            self._send_json(status_code, response)
        else:
            self._reject()

    def do_PUT(self):
        if urlparse(self.path).path == "/pause":
            status_code= self._accept_pause()
            self._send_json(status_code)
        elif urlparse(self.path).path == "/update-time":
            status_code, response = self._accept_current()
            self._send_json(status_code, response)
        elif urlparse(self.path).path == "/update-delta":
            status_code, response = self._accept_delta()
            self._send_json(status_code, response)
        elif urlparse(self.path).path == "/stop":
            status_code = self._accept_stop()
            self._send_json(status_code)
        else:
            self._reject()

    def do_POST(self):
        if urlparse(self.path).path == "/new-matches":
            status_code = self._accept_stop()
            self._send_json(status_code)
        else:
            self._reject()

    # reject everything else
    def do_PATCH(self):
        self._reject()

    def do_DELETE(self):
        self._reject()

    def _reject(self):
        self._send_json(405, {"error": "Method not allowed"})


if __name__ == "__main__":
    logger.info("Starting daemon")
    thread = threading.Thread(target=simulate_matches, daemon=True)
    thread.start()

    server = HTTPServer((HOST, PORT), ResultsMockerHandler)
    logger.info(f"Server running on http://{HOST}:{PORT}")
    server.serve_forever()
