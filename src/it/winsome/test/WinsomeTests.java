package it.winsome.test;

import it.winsome.client.ClientApplication;
import it.winsome.common.network.enums.NetResponseType;
import it.winsome.server.ServerMain;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

class WinsomeTests {
    private static ClientApplication client;

    static void runServerAsynchronously() throws Exception {
        AtomicBoolean didFail = new AtomicBoolean(false);
        Thread serverThread = new Thread(() -> {
            try {
                ServerMain.isTest = true;
                ServerMain.main(null);
            } catch (IOException e) {
                e.printStackTrace();
                didFail.set(true);
            }
        });
        serverThread.start();
        Thread.sleep(2000);
        if(didFail.get()) {
            throw new Exception("Server Startup failed!");
        }
    }

    @BeforeAll
    static void initializeComponents() throws Exception {
        runServerAsynchronously();
        client = new ClientApplication();
        client.loadConfiguration("client_config.json");
        client.initApplication();
    }

    @AfterAll
    static void stopComponents() {
        client.close();
        ServerMain.onQuit();
    }

    @Test
    void createFirstEightUsersSuccess() {
        assertEqualNetResponse(NetResponseType.Success,
                createUser("Ivan99", "logreco99_3anno", "Programmazione", "Serie Tv", "Dormire"),
                createUser("Sofia77", "sofyyyyy", "Programmazione", "Noodles", "Sushi"),
                createUser("Gianpaolooooo", "gianpierino", "Pesca", "Serie Tv", "Bere"),
                createUser("Pierino0_0", "bellaLaLegna", "Falegname"),
                createUser("QualcunoACaso", "passwordSuperSegreta", "Ingegnere", "Spazio", "Terra"),
                createUser("IndovinaChiSono", "haiIndovinatoBene", "Animatore", "Spiaggia", "Mare"),
                createUser("Reti2022", "retti200120002", "Programmazione", "Java", "Pacchetti"),
                createUser("WinnieThePoh", "winnyyyyyy", "Miele", "HK", "Cartoni"));
    }

    @Test
    void createBadUsers() {
        assertEqualNetResponse(NetResponseType.Success,
                createUser("Paperinoooo", "unaltrapassword!", "Scienza", "Matematica"));
        assertNotEqualNetResponse(NetResponseType.Success,
                createUser("Paperinoooo", "unaltrapassword!", "Scienza", "Matematica"),
                createUser("LungoNICKKKKKKKKKKKKKKKKKKK", "AAAAA", "Testing"),
                createUser("AB", "nickTroppoCorto", "Cannoli siciliani"),
                createUser("CaratteriIllegali!ò°é", "testRegex", "Falegname"),
                createUser("NickPro", "nessunTag"),
                createUser("PiuDi5Tag", "haiIndovinatoBene", "Animatore", "Spiaggia", "Mare", "Granchio", "Pan di stelle", "Luna"));
    }

    @Test
    void doSuccessfulLogin() {
        ensureLogout();
        createUser("succLoginUser", "test123", "Pesca");
        assertEqualNetResponse(NetResponseType.Success,
                loginUser("succLoginUser", "test123"));
    }

    @Test
    void doBadLogin() {
        ensureLogout();
        createUser("doBadLoginUser", "test123");
        assertNotEqualNetResponse(NetResponseType.Success,
                loginUser("AB", "psw"),
                loginUser("@badRegex!", "krgrkgk"),
                loginUser("doBadLogin123", "gvdfb"),
                loginUser("doBadLoginUser", "pswSbagliata"));
    }

    @Test
    void doSuccessfulLogout() {
        ensureLogout();
        createUser("succLogoutUser", "test123", "Pesca");
        loginUser("succLogoutUser", "test123");
        assertEqualNetResponse(NetResponseType.Success,
                client.sendCommand("logout", new String[] { }));
    }

    @Test
    void doBadLogout() {
        ensureLogout();
        createUser("badLogoutUser", "test123", "Pesca");
        // forget to login and try to logout
        assertNotEqualNetResponse(NetResponseType.Success,
                client.sendCommand("logout", new String[] { }));
    }

    @Test
    void doSuccessfulListUsers() {
        ensureLogout();
        createUser("succListUsers", "test123", "Pesca");
        loginUser("succListUsers", "test123");
        assertEqualNetResponse(NetResponseType.Success,
                client.sendCommand("list users", new String[] { }));
    }

    @Test
    void doBadListUsers() {
        ensureLogout();
        createUser("badListUsers", "test123", "Pesca");
        // forget to login
        assertNotEqualNetResponse(NetResponseType.Success,
                client.sendCommand("list users", new String[] { }));
    }

    @Test
    void doSuccessfulListFollowing() {
        ensureLogout();
        createUser("succListF.ing", "test123", "Pesca");
        loginUser("succListF.ing", "test123");
        assertEqualNetResponse(NetResponseType.Success,
                client.sendCommand("list following", new String[] { }));
    }

    @Test
    void doBadListFollowing() {
        ensureLogout();
        createUser("badListF.ing", "test123", "Pesca");
        // forget to login
        assertNotEqualNetResponse(NetResponseType.Success,
                client.sendCommand("list following", new String[] { }));
    }

    @Test
    void doSuccessfulListFollowers() {
        ensureLogout();
        createUser("succListF.ers", "test123", "Pesca");
        loginUser("succListF.ers", "test123");
        assertEqualNetResponse(NetResponseType.Success,
                client.sendCommand("list followers", new String[] { }));
    }

    @Test
    void doBadListFollowers() {
        ensureLogout();
        createUser("badListF.ers", "test123", "Pesca");
        // forget to login
        assertNotEqualNetResponse(NetResponseType.Success,
                client.sendCommand("list followers", new String[] { }));
    }

    @Test
    void doSuccessfulFollow() {
        ensureLogout();
        createUser("succFollow", "test123", "Pesca");
        createUser("succFollowTwo", "test123", "Pesca", "Youtube");
        loginUser("succFollow", "test123");
        assertEqualNetResponse(NetResponseType.Success,
                client.sendCommand("follow", new String[] { "succFollowTwo" }));
    }

    @Test
    void doBadFollow() {
        ensureLogout();
        createUser("badFollow", "test123", "Pesca");
        createUser("badFollowFriend", "test123", "Gaming");
        loginUser("badFollow", "test123");
        assertNotEqualNetResponse(NetResponseType.Success,
                client.sendCommand("follow", new String[] { "succFoiend56" }),
                client.sendCommand("follow", new String[] { "succ!_regex@end" }),
                client.sendCommand("follow", new String[] { "badFollow" }));

        ensureLogout();
        assertNotEqualNetResponse(NetResponseType.Success,
                client.sendCommand("follow", new String[] { "badFollowFriend" }));
    }

    @Test
    void doSuccessfulUnfollow() {
        ensureLogout();
        createUser("succUnfollow", "test123", "Pesca");
        createUser("succUnfollow2", "test123", "Pesca", "Youtube");
        loginUser("succUnfollow", "test123");
        client.sendCommand("follow", new String[] { "succUnfollow2" });
        assertEqualNetResponse(NetResponseType.Success,
                client.sendCommand("unfollow", new String[] { "succUnfollow2" }));
    }

    @Test
    void doBadUnfollow() {
        ensureLogout();
        createUser("badUnfollow", "test123", "Pesca");
        createUser("badUnfollow2", "test123", "Gaming");
        loginUser("badUnfollow", "test123");
        assertNotEqualNetResponse(NetResponseType.Success,
                client.sendCommand("unfollow", new String[] { "succFoiend56" }),
                client.sendCommand("unfollow", new String[] { "succ!_regex@end" }),
                client.sendCommand("unfollow", new String[] { "badUnfollow" }),
                client.sendCommand("unfollow", new String[] { "badUnfollow2" }));

        client.sendCommand("follow", new String[] { "badUnfollow2" });
        ensureLogout();
        assertNotEqualNetResponse(NetResponseType.Success,
                client.sendCommand("unfollow", new String[] { "badUnfollow2" }));
    }

    @Test
    void doSuccessfulViewBlog() {
        ensureLogout();
        createUser("succViewBlog", "test123", "Pesca");
        loginUser("succViewBlog", "test123");
        assertEqualNetResponse(NetResponseType.Success,
                client.sendCommand("blog", new String[] { }),
                client.sendCommand("blog", new String[] { "2" }));
    }

    @Test
    void doBadViewBlog() {
        ensureLogout();
        createUser("badViewBlog", "test123", "Pesca");
        // forget to login
        assertNotEqualNetResponse(NetResponseType.Success,
                client.sendCommand("blog", new String[] { }));

        loginUser("badViewBlog", "test123");
        assertNotEqualNetResponse(NetResponseType.Success,
                client.sendCommand("blog", new String[] { "-1" }));
    }

    @Test
    void doSuccessfulCreatePost() {
        ensureLogout();
        createUser("succCreatePost", "test123", "Pesca");
        loginUser("succCreatePost", "test123");
        assertEqualNetResponse(NetResponseType.Success,
                client.sendCommand("post", new String[] { "BTC sta scendendo!", "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Pellentesque cursus diam quam. Cras viverra scelerisque pretium. Fusce suscipit, mi finibus tempus convallis, nibh turpis laoreet orci, quis lobortis lectus magna." }),
                client.sendCommand("post", new String[] { "AAAAAAAAAAAAAA20", "RGERGERI GIERIGKERIG+PWEI+GIWE0èIG0èRUG0UERGUERUG0ERUG0ERGERGREGERJGJREGJREPJGJPER" }));
    }

    @Test
    void doBadCreatePost() {
        ensureLogout();
        createUser("badCreatePost", "test123", "Pesca");
        // forget to login
        assertNotEqualNetResponse(NetResponseType.Success,
                client.sendCommand("post", new String[] { "Bel titolo", "Contenuti pazzeschi!" }));

        loginUser("badCreatePost", "test123");
        assertNotEqualNetResponse(NetResponseType.Success,
                client.sendCommand("post", new String[] { "Titolo maggior di 20 caratteri quindi non permesso!", "Beh si" }),
                client.sendCommand("post", new String[] { "Contenuto > 500", "4pqqNvHnU5DKtkwi6ZLvCgG3sJ3PEUDlfUxYyPLdfwcnDpuflPFNonc4LG12RjBtHAvfID7BPu90xWgN2sGegXCNHZ7feSiaVO8cHxGBxj56xFFsLh2yZK2TPnR67b3juCmPib5WYE9U0reA0vlEzeXSXXb8n8cTCL79X884dF7mbb6K96LhJo6aDJIyzQeqpQLd0RRzDIcoREpx7j0PJffgZvP4dEZ6ThV8odkJ61YAn1257vAXDcfQ0BY0ETUgFqlJ5tWlsPVRXfVVlUDgAH2mhoopXiQ61VYg6D3dDh3xzKoMN9PT695mzCJsfZkr5mjenOPzPyQFeWLk8KsrpHDES92SayuNc0FuxaZx5dhsL5USF5SvJTeMPxP1D2wzSjBKVKu3qGbGvbJoutynHK55jPqYvfkN5GgH2dkXjG7nWTmeuGGWe20ZE4ua3Ze8hDWZnfKXXzMfDfdHErR3ruqRWmNK6hgmlqxeNSzJOZFQzncwpCKqndd" }));
    }

    @Test
    void doSuccessfulShowFeed() {
        ensureLogout();
        createUser("succShowFeed", "test123", "Pesca");
        loginUser("succShowFeed", "test123");
        assertEqualNetResponse(NetResponseType.Success,
                client.sendCommand("show feed", new String[] { }),
                client.sendCommand("show feed", new String[] { "2" }));
    }

    @Test
    void doBadShowFeed() {
        ensureLogout();
        createUser("badShowFeed", "test123", "Pesca");
        // forget to login
        assertNotEqualNetResponse(NetResponseType.Success,
                client.sendCommand("show feed", new String[] { }));

        loginUser("badShowFeed", "test123");
        assertNotEqualNetResponse(NetResponseType.Success,
                client.sendCommand("show feed", new String[] { "-1" }));
    }

    @Test
    void doSuccessfulShowPost() {
        ensureLogout();
        createUser("succShowPost", "test123", "Pesca");
        loginUser("succShowPost", "test123");

        client.sendCommand("post", new String[] { "Vendo PS4 Usata!", "140 euro trattabili! "});
        String postId = Integer.toString(getLatestPostId());
        assertEqualNetResponse(NetResponseType.Success,
                client.sendCommand("show post", new String[] { postId }));
    }

    @Test
    void doBadShowPost() {
        ensureLogout();
        createUser("badShowPost", "test123", "Pesca");
        loginUser("badShowPost", "test123");

        client.sendCommand("post", new String[] { "Compro casa a 10 euro!", "10 non trattabili! "});
        String postId = Integer.toString(getLatestPostId());
        String nextPostId = Integer.toString(getLatestPostId() + 1);
        assertNotEqualNetResponse(NetResponseType.Success,
                client.sendCommand("show post", new String[] { "-1" }),
                client.sendCommand("show post", new String[] { nextPostId }));

        ensureLogout();
        assertNotEqualNetResponse(NetResponseType.Success,
                client.sendCommand("show post", new String[] { postId }));
    }

    @Test
    void doSuccessfulDeletePost() {
        ensureLogout();
        createUser("succDelPost", "test123", "Pesca");
        loginUser("succDelPost", "test123");

        client.sendCommand("post", new String[] { "Colazione buona!", "Biscotti buoni! "});
        String postId = Integer.toString(getLatestPostId());
        assertEqualNetResponse(NetResponseType.Success,
                client.sendCommand("delete", new String[] { postId }));
    }

    @Test
    void doBadDeletePost() {
        ensureLogout();
        createUser("badDelPost", "test123", "Pesca");
        createUser("badDelPost2", "test123", "Pesca");
        loginUser("badDelPost2", "test123");
        client.sendCommand("post", new String[] { "Post di un altro!", "Non può eliminarlo! "});
        String otherPostId = Integer.toString(getLatestPostId());
        ensureLogout();
        loginUser("badDelPost", "test123");

        client.sendCommand("post", new String[] { "Nuovo telescopio!", "FIGOOOO! "});
        String postId = Integer.toString(getLatestPostId());
        String nextPostId = Integer.toString(getLatestPostId() + 1);
        assertNotEqualNetResponse(NetResponseType.Success,
                client.sendCommand("delete", new String[] { "-1" }),
                client.sendCommand("delete", new String[] { nextPostId }),
                client.sendCommand("delete", new String[] { otherPostId }));

        ensureLogout();
        assertNotEqualNetResponse(NetResponseType.Success,
                client.sendCommand("delete", new String[] { postId }));
    }

    @Test
    void doSuccessfulRewinPost() {
        ensureLogout();
        createUser("succRewinPost", "test123", "Pesca");
        createUser("succRewinPost2", "test123", "Pesca");
        loginUser("succRewinPost2", "test123");
        client.sendCommand("post", new String[] { "Come trovare lavoro!", "Eccovi una guida dettagliata su cosa fare: rgferghberhberhghberhberhberhbtenhrhbedhber!"});
        String lastPostId = Integer.toString(getLatestPostId());

        ensureLogout();
        loginUser("succRewinPost", "test123");
        client.sendCommand("follow", new String[] { "succRewinPost2" });
        assertEqualNetResponse(NetResponseType.Success,
                client.sendCommand("rewin", new String[] { lastPostId }));
    }

    @Test
    void doBadRewinPost() {
        ensureLogout();
        createUser("badRewinPost", "test123", "Pesca");
        createUser("badRewinPost2", "test123", "Pesca");
        loginUser("badRewinPost2", "test123");
        client.sendCommand("post", new String[] { "Il rewin!", "Anche se probabilmente questo test fallirà!"});
        String otherPostId = Integer.toString(getLatestPostId());

        ensureLogout();
        loginUser("badRewinPost", "test123");
        client.sendCommand("post", new String[] { "My post!", "Non posso rewinnare!"});
        String postId = Integer.toString(getLatestPostId());
        String nextPostId = Integer.toString(getLatestPostId() + 1);

        assertNotEqualNetResponse(NetResponseType.Success,
                client.sendCommand("rewin", new String[] { "-1" }),
                client.sendCommand("rewin", new String[] { otherPostId }),
                client.sendCommand("rewin", new String[] { postId }),
                client.sendCommand("rewin", new String[] { nextPostId }));

        client.sendCommand("follow", new String[] { "badRewinPost2" });
        ensureLogout();
        assertNotEqualNetResponse(NetResponseType.Success,
                client.sendCommand("rewin", new String[] { otherPostId }));
    }

    @Test
    void doSuccessfulRatePost() {
        ensureLogout();
        createUser("succRatePost", "test123", "Pesca");
        createUser("succRatePost2", "test123", "Pesca");
        loginUser("succRatePost2", "test123");
        client.sendCommand("post", new String[] { "Test rateeeee!", "Eccovi: rgferghberhberhghberhberhberhbtenhrhbedhber!"});
        String firstId = Integer.toString(getLatestPostId());
        client.sendCommand("post", new String[] { "2 Test rateeee!", "Eccovi: rgferghberhberhghberhberhberhbtenhrhbedhber!"});
        String secondId = Integer.toString(getLatestPostId());

        ensureLogout();
        loginUser("succRatePost", "test123");
        client.sendCommand("follow", new String[] { "succRatePost2" });
        assertEqualNetResponse(NetResponseType.Success,
                client.sendCommand("rate", new String[] { firstId, "+1" }),
                client.sendCommand("rate", new String[] { secondId, "-1" }));
    }

    @Test
    void doBadRatePost() {
        ensureLogout();
        createUser("badRatePost", "test123", "Pesca");
        createUser("badRatePost2", "test123", "Pesca");
        loginUser("badRatePost2", "test123");
        client.sendCommand("post", new String[] { "Test rateeeee!", "Eccovi: rgferghberhberhghberhberhberhbtenhrhbedhber!"});
        String firstId = Integer.toString(getLatestPostId());
        client.sendCommand("post", new String[] { "2 Test rateeee!", "Eccovi: rgferghberhberhghberhberhberhbtenhrhbedhber!"});
        String secondId = Integer.toString(getLatestPostId());

        ensureLogout();
        loginUser("badRatePost", "test123");
        client.sendCommand("post", new String[] { "Mio POst!", "Eccovi: rgferghberhberhghberhberhberhbtenhrhbedhber!"});
        String myId = Integer.toString(getLatestPostId());
        String failId = Integer.toString(getLatestPostId() + 1);
        assertNotEqualNetResponse(NetResponseType.Success,
                client.sendCommand("rate", new String[] { "-1", "+1" }),
                client.sendCommand("rate", new String[] { failId, "+1" }),
                client.sendCommand("rate", new String[] { failId, "UPVOTE" }),
                client.sendCommand("rate", new String[] { firstId, "+1" }),
                client.sendCommand("rate", new String[] { secondId, "+1" }),
                client.sendCommand("rate", new String[] { myId, "+1" })); // <- bad input

        client.sendCommand("follow", new String[] { "badRatePost2" });
        client.sendCommand("rate", new String[] { firstId, "+1" });
        client.sendCommand("rate", new String[] { secondId, "+1" });

        assertNotEqualNetResponse(NetResponseType.Success,
                client.sendCommand("rate", new String[] { firstId, "+1" }),
                client.sendCommand("rate", new String[] { secondId, "+1" }));
    }

    @Test
    void doSuccessfulAddComment() {
        ensureLogout();
        createUser("succAddComm", "test123", "Pesca");
        createUser("succAddComm2", "test123", "Pesca");
        loginUser("succAddComm2", "test123");
        client.sendCommand("post", new String[] { "Test rateeeee!", "Eccovi: rgferghberhberhghberhberhberhbtenhrhbedhber!"});
        String firstId = Integer.toString(getLatestPostId());
        client.sendCommand("post", new String[] { "2 Test rateeee!", "Eccovi: rgferghberhberhghberhberhberhbtenhrhbedhber!"});
        String secondId = Integer.toString(getLatestPostId());

        ensureLogout();
        loginUser("succAddComm", "test123");
        client.sendCommand("follow", new String[] { "succAddComm2" });
        assertEqualNetResponse(NetResponseType.Success,
                client.sendCommand("comment", new String[] { firstId, "Commento pazzesco minore di 150 caratteri!" }),
                client.sendCommand("comment", new String[] { secondId, "Commento sul secondo post!" }),
                client.sendCommand("comment", new String[] { secondId, "E nel dubbio ne metto un altro!" }));
    }

    @Test
    void doBadAddComment() {
        ensureLogout();
        createUser("badAddComm", "test123", "Pesca");
        createUser("badAddComm2", "test123", "Pesca");
        loginUser("badAddComm2", "test123");
        client.sendCommand("post", new String[] { "Test rateeeee!", "Eccovi: rgferghberhberhghberhberhberhbtenhrhbedhber!"});
        String firstId = Integer.toString(getLatestPostId());
        client.sendCommand("post", new String[] { "2 Test rateeee!", "Eccovi: rgferghberhberhghberhberhberhbtenhrhbedhber!"});
        String secondId = Integer.toString(getLatestPostId());

        ensureLogout();
        loginUser("badAddComm", "test123");
        client.sendCommand("post", new String[] { "Mio POst!", "Eccovi: rgferghberhberhghberhberhberhbtenhrhbedhber!"});
        String myId = Integer.toString(getLatestPostId());
        String failId = Integer.toString(getLatestPostId() + 1);
        assertNotEqualNetResponse(NetResponseType.Success,
                client.sendCommand("comment", new String[] { "-1", "Commento con id negativo" }),
                client.sendCommand("comment", new String[] { failId, "Commento su id non esistente" }),
                client.sendCommand("comment", new String[] { firstId, "Commento su id non nel feed" }),
                client.sendCommand("comment", new String[] { secondId, "Commento su id non nel feed" }),
                client.sendCommand("comment", new String[] { myId, "Auto commento non possibile!" })); // <- bad input

        client.sendCommand("follow", new String[] { "badAddComm2" });
        client.sendCommand("comment", new String[] { firstId, "Commento accettato!" });

        assertNotEqualNetResponse(NetResponseType.Success,
                client.sendCommand("comment", new String[] { firstId, "PIU DI 150 CARATTERI AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" }),
                client.sendCommand("comment", new String[] { secondId, "PIU DI 150 CARATTERI AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" }));
    }

    @Test
    void doSuccessfulGetWallet() {
        ensureLogout();
        createUser("succWallet", "test123", "Pesca");
        loginUser("succWallet", "test123");
        assertEqualNetResponse(NetResponseType.Success,
                client.sendCommand("wallet", new String[] { }));
    }

    @Test
    void doBadWallet() {
        ensureLogout();
        createUser("badWallet", "test123", "Pesca");
        // forget to login
        assertNotEqualNetResponse(NetResponseType.Success,
                client.sendCommand("wallet", new String[] { }));
    }

    @Test
    void doSuccessfulGetWalletBTC() {
        ensureLogout();
        createUser("succWalletBtc", "test123", "Pesca");
        loginUser("succWalletBtc", "test123");
        assertEqualNetResponse(NetResponseType.Success,
                client.sendCommand("wallet", new String[] { "btc" }));
    }

    @Test
    void doBadWalletBTC() {
        ensureLogout();
        createUser("badWalletBtc", "test123", "Pesca");
        // forget to login
        assertNotEqualNetResponse(NetResponseType.Success,
                client.sendCommand("wallet", new String[] { "btc" }));
    }

    NetResponseType loginUser(String username, String password) {
        return client.sendCommand("login", new String[] { username, password });
    }

    NetResponseType createUser(String username, String password, String... hobby) {
        String[] args = new String[2 + hobby.length];
        args[0] = username;
        args[1] = password;
        for(int i = 2; i < args.length; i++) {
            args[i] = hobby[i - 2];
        }

        return client.sendCommand("register", args);
    }

    void assertEqualNetResponse(NetResponseType expected, NetResponseType... result) {
        for (NetResponseType r : result)
         Assertions.assertEquals(expected.getId(), r.getId());
    }

    void assertNotEqualNetResponse(NetResponseType expected, NetResponseType... result) {
        for (NetResponseType r : result)
            Assertions.assertNotEquals(expected.getId(), r.getId());
    }

    void ensureLogout() {
        if(client.isLoggedIn()) {
            System.out.println("Quick logout!");
            client.sendCommand("logout", new String[] { });
        }
    }

    int getLatestPostId() {
        return ServerMain.getServerLogic().getLatestPostId();
    }
}