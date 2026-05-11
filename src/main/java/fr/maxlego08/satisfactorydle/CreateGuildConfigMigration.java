package fr.maxlego08.satisfactorydle;

import fr.maxlego08.sarah.database.Migration;

public class CreateGuildConfigMigration extends Migration {

    @Override
    public void up() {
        create("guild_configs", schema -> {
            schema.string("guild_id", 20).primary();
            schema.string("locale", 5);
            schema.string("active_modes", 255);
        });
    }
}
