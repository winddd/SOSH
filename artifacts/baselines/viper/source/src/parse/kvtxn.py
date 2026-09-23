# uncompyle6 version 3.9.2
# Python bytecode version base 3.8.0 (3413)
# Decompiled from: Python 3.8.10 (default, Mar 18 2025, 20:04:55) 
# [GCC 9.4.0]
# Embedded file name: /home/windkl/git_repos/Viper_series/Viper_public/src/parse/kvtxn.py
# Compiled at: 2024-11-30 19:40:36
# Size of source mod 2**32: 678 bytes
import json
from typing import Dict

class KvTxn(Dict):

    def __init__(self, id=None):
        self["id"] = id
        self["value"] = []

    def addOp(self, mop):
        self["value"].append(mop)

    def setTxnId(self, id):
        self["id"] = id

    def __str__(self):
        return json.dumps(self)
