package com.decacagle.query;

import org.rekex.helper.anno.Ch;
import org.rekex.helper.anno.StrWs;
import org.rekex.helper.datatype.Opt;
import org.rekex.parser.PegParser;
import org.rekex.spec.Ctor;
import org.rekex.spec.Regex;

public interface MCDBGrammar {

    String WS_CHARS = " \t";
    enum Ws { @StrWs(value = " ", wsChars = WS_CHARS) SPACE }

    record Identifier(@Regex("[a-zA-Z_][a-zA-Z0-9_]*") String value) {}

    record NumLit(@Regex("-?[0-9]+") String raw) {
        public int toInt() { return Integer.parseInt(raw); }
    }

    record JsonBody(@Regex("[\\s\\S]+") String raw) {}

    record WhereClause(
            @StrWs(value = "WHERE", wsChars = WS_CHARS) Void kw,
            Identifier key,
            @Ch("=") Void eq,
            @Regex("[^\\s]+") String target
    ) {}

    sealed interface MCDBQuery permits
            SelectAllQuery, SelectWhereQuery, SelectIdQuery,
            InsertQuery, UpdateQuery,
            DeleteAllQuery, DeleteIdQuery, DeleteTableQuery,
            CreateTableQuery, ProtectQuery, UnprotectQuery {}

    record SelectAllQuery(
            @StrWs(value = "SELECT", wsChars = WS_CHARS) Void kw1,
            @StrWs(value = "*", wsChars = WS_CHARS) Void star,
            @StrWs(value = "FROM", wsChars = WS_CHARS) Void kw2,
            Identifier table
    ) implements MCDBQuery {}

    record SelectWhereQuery(
            @StrWs(value = "SELECT", wsChars = WS_CHARS) Void kw1,
            @StrWs(value = "*", wsChars = WS_CHARS) Void star,
            @StrWs(value = "FROM", wsChars = WS_CHARS) Void kw2,
            Identifier table,
            WhereClause where
    ) implements MCDBQuery {}

    record SelectIdQuery(
            @StrWs(value = "SELECT", wsChars = WS_CHARS) Void kw1,
            NumLit id,
            @StrWs(value = "FROM", wsChars = WS_CHARS) Void kw2,
            Identifier table
    ) implements MCDBQuery {}

    record InsertQuery(
            @StrWs(value = "INSERT", wsChars = WS_CHARS) Void kw1,
            @StrWs(value = "INTO", wsChars = WS_CHARS) Void kw2,
            Identifier table,
            @StrWs(value = "VALUE", wsChars = WS_CHARS) Void kw3,
            JsonBody body
    ) implements MCDBQuery {}

    record UpdateQuery(
            @StrWs(value = "UPDATE", wsChars = WS_CHARS) Void kw1,
            NumLit id,
            @StrWs(value = "IN", wsChars = WS_CHARS) Void kw2,
            Identifier table,
            @StrWs(value = "SET", wsChars = WS_CHARS) Void kw3,
            JsonBody body
    ) implements MCDBQuery {}

    record DeleteAllQuery(
            @StrWs(value = "DELETE", wsChars = WS_CHARS) Void kw1,
            @StrWs(value = "*", wsChars = WS_CHARS) Void star,
            @StrWs(value = "FROM", wsChars = WS_CHARS) Void kw2,
            Identifier table
    ) implements MCDBQuery {}

    record DeleteIdQuery(
            @StrWs(value = "DELETE", wsChars = WS_CHARS) Void kw1,
            NumLit id,
            @StrWs(value = "FROM", wsChars = WS_CHARS) Void kw2,
            Identifier table
    ) implements MCDBQuery {}

    record DeleteTableQuery(
            @StrWs(value = "DELETE", wsChars = WS_CHARS) Void kw1,
            @StrWs(value = "TABLE", wsChars = WS_CHARS) Void kw2,
            Identifier table
    ) implements MCDBQuery {}

    record CreateTableQuery(
            @StrWs(value = "CREATE", wsChars = WS_CHARS) Void kw1,
            @StrWs(value = "TABLE", wsChars = WS_CHARS) Void kw2,
            Identifier table
    ) implements MCDBQuery {}

    record ProtectQuery(
            @StrWs(value = "PROTECT", wsChars = WS_CHARS) Void kw,
            Identifier table,
            @Regex("[cruCRU*]{1,4}") String flags
    ) implements MCDBQuery {}

    record UnprotectQuery(
            @StrWs(value = "PROTECT", wsChars = WS_CHARS) Void kw,
            Identifier table,
            @StrWs(value = "REMOVE", wsChars = WS_CHARS) Void remove
    ) implements MCDBQuery {}

    static PegParser<MCDBQuery> parser() {
        return PegParser.of(MCDBQuery.class);
    }
}