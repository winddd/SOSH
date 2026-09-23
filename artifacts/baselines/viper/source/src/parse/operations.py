# uncompyle6 version 3.9.2
# Python bytecode version base 3.8.0 (3413)
# Decompiled from: Python 3.8.10 (default, Mar 18 2025, 20:04:55) 
# [GCC 9.4.0]
# Embedded file name: /home/windkl/git_repos/Viper_series/Viper_public/src/parse/operations.py
# Compiled at: 2024-11-30 19:37:44
# Size of source mod 2**32: 817 bytes
from typing import List

class ReadOp(List):

    def __init__(self, key_hash, val_hash):
        super().__init__()
        self.append("r")
        self.append(key_hash)
        self.append(val_hash)
        self.append(False)


class WriteOp(List):

    def __init__(self, key_hash, val_hash):
        super().__init__()
        self.append("w")
        self.append(key_hash)
        self.append(val_hash)
        self.append(True)
