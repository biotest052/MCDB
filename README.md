# MCDB
By Noah Cagle

MCDB is a plugin for Spigot Minecraft servers that enables you to turn your Minecraft server into a full-stack web server. It can be used to file storage, data storage, and can even serve webpages. It can receive SQL-like queries through its /query endpoint, and it is capable of basic data-level security.

## Be Aware!

You are looking at one of the earliest commits to this project. I have not yet properly documented this project, however I am currently working on a comprehensive guide to using MCDB.

As of right now, MCDB automatically hosts its HTTP server from port 8000. In the future, you will be able to change this port from a config.yml file, but for now, the only way to change the port is to modify the port from the source code and then build your own binary.

I have built an example website that I call MCSocial. It's a stupidly simple Minecraft-themed social media website, however it serves as a proof of concept for the MCDB project. Currently, this repo is set up to provide the ability to play with MCSocial and the MCDB for yourself. In the future, MCDB will be a bit more flexible, allowing anyone to host anything from it with ease.

# Launching MCSocial for yourself

The world found in the Boilderplate World folder comes with almost everything you'll need to host MCSocial. It contains four tables: users, authTokens, profiles, and posts. It also comes with the MCSocial source code and default profile picture pre-loaded.

To run MCSocial, first set up a Spigot multiplayer server running on v1.21.5. Then, drop the MCDB jar into your plugins folder. Finally, replace the auto-generated 'world' folder with the folder found in the Boilderplate World folder of this repository.

To access MCSocial, go to your web browser and navigate to http://localhost:8000/index.html
